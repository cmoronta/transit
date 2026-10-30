package com.transitbike.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.maplibre.android.MapLibre

class TransitBikeApplication : Application() {

    companion object {
        const val NAV_NOTIFICATION_CHANNEL_ID = "bike_nav_channel"
        lateinit var instance: TransitBikeApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize MapLibre Native SDK with OpenFreeMap / OSM compatibility
        MapLibre.getInstance(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NAV_NOTIFICATION_CHANNEL_ID,
                getString(R.string.nav_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active turn-by-turn guidance and distance remaining"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
