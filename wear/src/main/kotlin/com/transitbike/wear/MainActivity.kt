package com.transitbike.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.ambient.AmbientLifecycleObserver
import com.google.android.gms.wearable.Wearable
import com.transitbike.shared.datalayer.DataLayerContract
import com.transitbike.shared.model.FavoritePlace
import com.transitbike.shared.model.LatLng
import com.transitbike.shared.model.RecentRide
import com.transitbike.wear.sync.WearListenerService
import com.transitbike.wear.ui.RideScreen
import com.transitbike.wear.ui.WearHomeScreen
import com.transitbike.wear.ui.theme.WearAppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private var isAmbientMode by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientMode = true
        }

        override fun onExitAmbient() {
            isAmbientMode = false
        }

        override fun onUpdateAmbient() {}
    }

    private val ambientObserver by lazy { AmbientLifecycleObserver(this, ambientCallback) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        setContent {
            WearAppTheme {
                val navState by WearListenerService.syncedNavState.collectAsState()
                val route by WearListenerService.syncedRoute.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                // Default favorites and recents (also synced from phone)
                val favorites = remember {
                    listOf(
                        FavoritePlace("fav_home", "Home", "Midtown", LatLng(33.7816, -84.3831), "home"),
                        FavoritePlace("fav_work", "Work", "Ponce City Market", LatLng(33.7725, -84.3656), "work")
                    )
                }

                val recents = remember {
                    listOf(
                        RecentRide("rec_1", "Ponce City Market", LatLng(33.7725, -84.3656), System.currentTimeMillis() - 86400000L, 4510.0, 840L)
                    )
                }

                if (navState != null) {
                    RideScreen(
                        navState = navState,
                        route = route,
                        isAmbient = isAmbientMode,
                        onStopRide = {
                            coroutineScope.launch {
                                try {
                                    val nodes = nodeClient.connectedNodes.await()
                                    for (node in nodes) {
                                        messageClient.sendMessage(node.id, DataLayerContract.PATH_TRIP_STOP, ByteArray(0)).await()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    )
                } else {
                    WearHomeScreen(
                        favorites = favorites,
                        recents = recents,
                        onSelectDestination = { destName ->
                            coroutineScope.launch {
                                try {
                                    val nodes = nodeClient.connectedNodes.await()
                                    for (node in nodes) {
                                        messageClient.sendMessage(node.id, DataLayerContract.PATH_TRIP_START, destName.encodeToByteArray()).await()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}
