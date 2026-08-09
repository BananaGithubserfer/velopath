package com.example.velopath.core.map

import android.content.Context
import com.yandex.mapkit.MapKitFactory
import java.util.concurrent.atomic.AtomicBoolean

object MapKitInitializer {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            MapKitFactory.initialize(context.applicationContext)
        }
    }
}
