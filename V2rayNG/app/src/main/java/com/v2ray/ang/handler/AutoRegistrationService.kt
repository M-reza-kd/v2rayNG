package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Registers this device against the backend and retrieves a subscriptionId.
 *
 * Endpoint:
 *   GET http://dev.s.fastshot.net:8001/register?deviceId={uuid}
 * Response:
 *   {"subscriptionId":"..."}
 */
object AutoRegistrationService {

    private data class RegistrationResponse(
        val subscriptionId: String? = null,
    )

    suspend fun registerDevice(deviceId: String): Result<String> {
        if (deviceId.isBlank()) {
            return Result.failure(IllegalArgumentException("deviceId is blank"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8")
                val primaryUrl = "${AppConfig.REGISTRATION_URL}?deviceId=$encodedDeviceId"
                val fallbackUrl = "${AppConfig.REGISTRATION_URL_FALLBACK}?deviceId=$encodedDeviceId"

                Log.i(AppConfig.TAG, "Device registration request: $primaryUrl")

                val response = try {
                    // Some setups may block non-browser User-Agents; use a browser-like UA.
                    HttpUtil.getUrlContentWithUserAgent(
                        primaryUrl,
                        AppConfig.CUSTOM_API_USER_AGENT,
                        AppConfig.CUSTOM_API_TIMEOUT_MS,
                        0
                    )
                } catch (e: UnknownHostException) {
                    // Device DNS cannot resolve the domain; fall back to pinned IP.
                    Log.w(
                        AppConfig.TAG,
                        "Device registration DNS failed for host; retrying via fallback IP: ${e.message}"
                    )
                    Log.i(AppConfig.TAG, "Device registration fallback request: $fallbackUrl")
                    HttpUtil.getUrlContentWithUserAgent(
                        fallbackUrl,
                        AppConfig.CUSTOM_API_USER_AGENT,
                        AppConfig.CUSTOM_API_TIMEOUT_MS,
                        0
                    )
                }

                if (response.isBlank()) {
                    Log.e(AppConfig.TAG, "Device registration got empty response")
                    return@withContext Result.failure(
                        IllegalStateException("Empty response from registration endpoint")
                    )
                }

                Log.i(AppConfig.TAG, "Device registration response: $response")

                val parsed = JsonUtil.fromJson(response, RegistrationResponse::class.java)
                val subId = parsed?.subscriptionId?.trim()

                if (subId.isNullOrBlank()) {
                    Log.e(AppConfig.TAG, "Device registration missing subscriptionId in response: $response")
                    return@withContext Result.failure(
                        IllegalStateException("Missing subscriptionId in response")
                    )
                }

                Log.i(AppConfig.TAG, "Device registration succeeded; subscriptionId=$subId")
                Result.success(subId)
            } catch (e: Exception) {
                Log.e(
                    AppConfig.TAG,
                    "Device registration failed: ${e.javaClass.name}: ${e.message}\n${Log.getStackTraceString(e)}"
                )
                Result.failure(e)
            }
        }
    }
}

