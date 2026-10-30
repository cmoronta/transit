package com.transitbike.mobile.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.transitbike.mobile.MainActivity
import com.transitbike.mobile.R
import com.transitbike.mobile.TransitBikeApplication
import com.transitbike.mobile.sync.PhoneWearSync
import com.transitbike.shared.datalayer.DataLayerContract
import com.transitbike.shared.model.LatLng
import com.transitbike.shared.model.NavState
import com.transitbike.shared.model.RouteModel
import com.transitbike.shared.model.UnitSystem
import com.transitbike.shared.nav.NavigationEngine
import com.transitbike.shared.nav.UnitFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val EXTRA_ROUTE_JSON = "extra_route_json"

        private val _activeState = MutableStateFlow<NavState?>(null)
        val activeState: StateFlow<NavState?> = _activeState.asStateFlow()

        private val _currentRoute = MutableStateFlow<RouteModel?>(null)
        val currentRoute: StateFlow<RouteModel?> = _currentRoute.asStateFlow()

        fun startService(context: Context, route: RouteModel) {
            val intent = Intent(context, NavigationForegroundService::class.java).apply {
                putExtra(EXTRA_ROUTE_JSON, DataLayerContract.json.encodeToString(com.transitbike.shared.model.RouteModel.serializer(), route))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NavigationForegroundService::class.java)
            context.stopService(intent)
            _activeState.value = null
            _currentRoute.value = null
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wearSync: PhoneWearSync
    private var engine: NavigationEngine? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        wearSync = PhoneWearSync(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routeJson = intent?.getStringExtra(EXTRA_ROUTE_JSON)
        if (routeJson != null) {
            val route = DataLayerContract.json.decodeFromString(com.transitbike.shared.model.RouteModel.serializer(), routeJson)
            _currentRoute.value = route
            initNavigation(route)
        }

        val notification = buildNotification("Starting turn-by-turn navigation...", "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationTracking()
        return START_STICKY
    }

    private fun initNavigation(route: RouteModel) {
        val newEngine = NavigationEngine(route)
        engine = newEngine

        // Initial sync of route to watch
        serviceScope.launch {
            wearSync.syncActiveRoute(route)
        }

        // Collect cues from engine and broadcast haptics to watch
        serviceScope.launch {
            newEngine.cueEvents.collect { cue ->
                wearSync.sendCueHaptic(cue)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second intervals for responsive bike navigation
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(2.0f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val currentLoc = LatLng(loc.latitude, loc.longitude)
                val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0

                val (newState, _) = engine?.updateLocation(currentLoc, speed) ?: return
                _activeState.value = newState

                // Sync state to watch
                serviceScope.launch {
                    wearSync.syncNavState(newState)
                }

                // Update notification banner
                val maneuver = newState.currentManeuver
                val instruction = maneuver?.instruction ?: "Following route"
                val distanceStr = UnitFormatter.formatDistance(newState.distanceToNextManeuverMeters, UnitSystem.IMPERIAL)
                updateNotification(instruction, distanceStr)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TransitBikeApplication.NAV_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(instruction: String, distance: String) {
        val notification = buildNotification(instruction, "In $distance")
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        serviceScope.launch {
            wearSync.sendTripStop()
        }
        _activeState.value = null
        _currentRoute.value = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
