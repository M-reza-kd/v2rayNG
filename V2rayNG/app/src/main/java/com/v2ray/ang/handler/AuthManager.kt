package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UserCredentials

object AuthManager {

    /**
     * Builds the subscription URL using the stored credentials.
     *
     * Subscription URL shape:
     *   {subscriptionBaseUrl}/{subscriptionId}
     *
     * @return The full subscription URL, or null if credentials are not available.
     */
    fun getSubscriptionUrl(): String? {
        val credentials = MmkvManager.getUserCredentials() ?: return null

        if (credentials.subscriptionId.isBlank() || credentials.serverUrl.isBlank()) {
            return null
        }

        return buildSubscriptionUrl(credentials.serverUrl, credentials.subscriptionId)
    }

    /**
     * Builds a subscription URL from server URL and subscription ID.
     *
     * @param serverUrl The subscription base URL.
     * @param subscriptionId The subscription ID.
     * @return The full subscription URL.
     */
    fun buildSubscriptionUrl(serverUrl: String, subscriptionId: String): String {
        // Remove trailing slash from server URL if present
        val baseUrl = serverUrl.trimEnd('/')
        // Build the subscription URL: {baseUrl}/{subscriptionId}
        return "$baseUrl/$subscriptionId"
    }

    /**
     * Checks if the device is registered (i.e., we have a subscription id stored).
     */
    fun isDeviceRegistered(): Boolean {
        val credentials = MmkvManager.getUserCredentials() ?: return false
        return credentials.subscriptionId.isNotBlank() && credentials.serverUrl.isNotBlank()
    }

    /**
     * Checks if the user is currently "logged in".
     *
     * In the device-registration flow, "logged in" simply means the device has been registered
     * and we have a subscription id stored.
     *
     * @return True if user is logged in with valid credentials.
     */
    fun isLoggedIn(): Boolean {
        return isDeviceRegistered()
    }

    /**
     * Logs out the user by clearing stored credentials and removing associated configs.
     */
    fun logout() {
        // Get the subscription ID before clearing credentials
        val subId = getSubscriptionId()
        
        // Remove all configs associated with this subscription
        if (!subId.isNullOrBlank()) {
            Log.i(AppConfig.TAG, "Logout: Removing configs for subscription ID: $subId")
            MmkvManager.removeServerViaSubid(subId)
        }
        
        // Clear user credentials
        MmkvManager.clearUserCredentials()
        
        Log.i(AppConfig.TAG, "Logout: User logged out successfully")
    }

    /**
     * Gets the current user's subscription ID.
     *
     * @return The subscription ID, or null if not logged in.
     */
    fun getSubscriptionId(): String? {
        return MmkvManager.getUserCredentials()?.subscriptionId?.takeIf { it.isNotBlank() }
    }
}

