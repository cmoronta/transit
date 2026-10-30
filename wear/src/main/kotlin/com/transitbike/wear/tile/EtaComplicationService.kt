package com.transitbike.wear.tile

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.transitbike.wear.MainActivity
import com.transitbike.wear.sync.WearListenerService

class EtaComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("14m").build(),
            contentDescription = PlainComplicationText.Builder("14 minutes remaining").build()
        ).setTitle(PlainComplicationText.Builder("ETA").build()).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val navState = WearListenerService.syncedNavState.value
        val remainingMin = if (navState != null) {
            val mins = (navState.remainingDurationSeconds + 30) / 60
            "${mins}m"
        } else {
            "--"
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(remainingMin).build(),
            contentDescription = PlainComplicationText.Builder("Bike Navigation ETA").build()
        )
            .setTitle(PlainComplicationText.Builder("ETA").build())
            .setTapAction(tapIntent)
            .build()
    }
}
