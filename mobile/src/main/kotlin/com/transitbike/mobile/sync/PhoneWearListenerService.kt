package com.transitbike.mobile.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.transitbike.mobile.service.NavigationForegroundService
import com.transitbike.shared.datalayer.DataLayerContract

class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataLayerContract.PATH_TRIP_STOP -> {
                NavigationForegroundService.stopService(this)
            }
        }
    }
}
