package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import java.util.UUID

/**
 * Provides a stable per-install device identifier without permissions.
 *
 * We generate a UUID once and persist it in MMKV.
 * It will remain stable until the user clears app data.
 */
object DeviceIdManager {

    @Synchronized
    fun getDeviceId(): String {
        val existing = MmkvManager.getDeviceUuid()
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        MmkvManager.saveDeviceUuid(generated)
        Log.i(AppConfig.TAG, "Generated new device UUID")
        return generated
    }

    fun hasDeviceId(): Boolean {
        return !MmkvManager.getDeviceUuid().isNullOrBlank()
    }
}
