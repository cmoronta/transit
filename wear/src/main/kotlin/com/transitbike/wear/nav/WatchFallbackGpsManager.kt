package com.transitbike.wear.nav

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.transitbike.shared.model.LatLng
import com.transitbike.shared.model.NavState
import com.transitbike.shared.model.RouteModel
import com.transitbike.shared.nav.NavigationEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchFallbackGpsManager(
    private val context: Context,
    private val onCueTriggered: (com.transitbike.shared.model.CueEvent) -> Unit
) {

    companion object {
        const val PHONE_HEARTBEAT_TIMEOUT_MS = 6000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private var locationCallback: LocationCallback? = null

    private var localEngine: NavigationEngine? = null
    private var cachedRoute: RouteModel? = null

    private var lastPhoneUpdateMs = 0L
    private var isWatchGpsActive = false

    private val _fallbackState = MutableStateFlow<NavState?>(null)
    val fallbackState: StateFlow<NavState?> = _fallbackState.asStateFlow()

    fun updateCachedRoute(route: RouteModel) {
        cachedRoute = route
        localEngine = NavigationEngine(route)
        scope.launch {
            localEngine?.cueEvents?.collect { cue ->
                onCueTriggered(cue)
            }
        }
    }

    fun onPhoneUpdateReceived(state: NavState) {
        lastPhoneUpdateMs = System.currentTimeMillis()
        if (isWatchGpsActive) {
            stopWatchGps()
        }
    }

    fun startHeartbeatMonitor() {
        scope.launch {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val elapsedSincePhone = now - lastPhoneUpdateMs
                if (elapsedSincePhone > PHONE_HEARTBEAT_TIMEOUT_MS && !isWatchGpsActive && cachedRoute != null) {
                    startWatchGps()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWatchGps() {
        isWatchGpsActive = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)
                val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
                val (state, _) = localEngine?.updateLocation(latLng, speed) ?: return
                _fallbackState.value = state
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun stopWatchGps() {
        isWatchGpsActive = false
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    fun stop() {
        stopWatchGps()
        scope.cancel()
    }
}
