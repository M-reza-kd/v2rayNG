package com.v2ray.ang.dto

data class UserCredentials(
        var subscriptionId: String = "",
        var serverUrl: String = "", // Your API server base URL
        var isLoggedIn: Boolean = false,
        val loginTime: Long = System.currentTimeMillis()
)
