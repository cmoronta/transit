package com.transitbike.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.transitbike.shared.datalayer.DataLayerContract
import com.transitbike.shared.model.CueEvent
import com.transitbike.shared.model.NavState
import com.transitbike.shared.model.RouteModel
import com.transitbike.wear.nav.HapticCueEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearListenerService : WearableListenerService() {

    companion object {
        private val _syncedRoute = MutableStateFlow<RouteModel?>(null)
        val syncedRoute: StateFlow<RouteModel?> = _syncedRoute.asStateFlow()

        private val _syncedNavState = MutableStateFlow<NavState?>(null)
        val syncedNavState: StateFlow<NavState?> = _syncedNavState.asStateFlow()
    }

    private val hapticEngine by lazy { HapticCueEngine(this) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                when (uri.path) {
                    DataLayerContract.PATH_ACTIVE_ROUTE -> {
                        val bytes = event.dataItem.data ?: continue
                        try {
                            val route = DataLayerContract.decodeRoute(bytes)
                            _syncedRoute.value = route
                        } catch (_: Exception) {}
                    }
                    DataLayerContract.PATH_NAV_STATE -> {
                        val bytes = event.dataItem.data ?: continue
                        try {
                            val state = DataLayerContract.decodeNavState(bytes)
                            _syncedNavState.value = state
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataLayerContract.PATH_NAV_CUE -> {
                val bytes = messageEvent.data
                try {
                    val cue = DataLayerContract.decodeCueEvent(bytes)
                    hapticEngine.playCueHaptic(cue)
                } catch (_: Exception) {}
            }
            DataLayerContract.PATH_TRIP_STOP -> {
                _syncedRoute.value = null
                _syncedNavState.value = null
            }
        }
    }
}
