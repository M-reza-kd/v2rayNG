package com.v2ray.ang.handler

import com.v2ray.ang.dto.UserCredentials

object AuthManager {

    /**
     * Builds the subscription URL using the stored credentials.
     * Returns the full URL with the subscription ID as a token parameter.
     *
     * @return The full subscription URL, or null if credentials are not available.
     */
    fun getSubscriptionUrl(): String? {
        val credentials = MmkvManager.getUserCredentials() ?: return null
        
        if (!credentials.isLoggedIn || credentials.subscriptionId.isEmpty() || credentials.serverUrl.isEmpty()) {
            return null
        }

        return buildSubscriptionUrl(credentials.serverUrl, credentials.subscriptionId)
    }

    /**
     * Builds a subscription URL from server URL and subscription ID.
     *
     * @param serverUrl The base server URL.
     * @param subscriptionId The subscription ID (used as token).
     * @return The full subscription URL.
     */
    fun buildSubscriptionUrl(serverUrl: String, subscriptionId: String): String {
        // Remove trailing slash from server URL if present
        val baseUrl = serverUrl.trimEnd('/')
        // Build the subscription URL: /api/subscription?token=subscriptionId
        return "$baseUrl/api/subscription?token=$subscriptionId"
    }

    /**
     * Checks if the user is currently logged in.
     *
     * @return True if user is logged in with valid credentials.
     */
    fun isLoggedIn(): Boolean {
        return MmkvManager.isUserLoggedIn()
    }

    /**
     * Logs out the user by clearing stored credentials.
     */
    fun logout() {
        MmkvManager.clearUserCredentials()
    }

    /**
     * Gets the current user's subscription ID.
     *
     * @return The subscription ID, or null if not logged in.
     */
    fun getSubscriptionId(): String? {
        return MmkvManager.getUserCredentials()?.subscriptionId
    }
}

