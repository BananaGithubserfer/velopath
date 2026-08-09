package com.example.velopath

import android.app.Application
import android.util.Log
import com.yandex.mapkit.MapKitFactory

class VeloPathApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
            Log.w("VeloPathApplication", "MAPKIT_API_KEY is empty. Yandex MapKit is disabled.")
            return
        }

        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
    }
}
