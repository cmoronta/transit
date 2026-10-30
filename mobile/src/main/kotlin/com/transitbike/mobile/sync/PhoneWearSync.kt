package com.transitbike.mobile.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.transitbike.shared.datalayer.DataLayerContract
import com.transitbike.shared.model.CueEvent
import com.transitbike.shared.model.NavState
import com.transitbike.shared.model.RouteModel
import kotlinx.coroutines.tasks.await

class PhoneWearSync(private val context: Context) {

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun syncActiveRoute(route: RouteModel) {
        try {
            val bytes = DataLayerContract.encodeRoute(route)
            val putDataReq = PutDataRequest.create(DataLayerContract.PATH_ACTIVE_ROUTE).apply {
                data = bytes
                setUrgent()
            }
            dataClient.putDataItem(putDataReq).await()
        } catch (_: Exception) {
            // Watch may be temporarily disconnected; state persists in DataClient
        }
    }

    suspend fun syncNavState(state: NavState) {
        try {
            val bytes = DataLayerContract.encodeNavState(state)
            val putDataReq = PutDataRequest.create(DataLayerContract.PATH_NAV_STATE).apply {
                data = bytes
                setUrgent()
            }
            dataClient.putDataItem(putDataReq).await()
        } catch (_: Exception) {
        }
    }

    suspend fun sendCueHaptic(cue: CueEvent) {
        try {
            val bytes = DataLayerContract.encodeCueEvent(cue)
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, DataLayerContract.PATH_NAV_CUE, bytes).await()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun sendTripStop() {
        try {
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, DataLayerContract.PATH_TRIP_STOP, ByteArray(0)).await()
            }
        } catch (_: Exception) {
        }
    }
}
