package com.transitbike.wear

import android.app.Application

class TransitBikeWearApplication : Application() {

    companion object {
        lateinit var instance: TransitBikeWearApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
