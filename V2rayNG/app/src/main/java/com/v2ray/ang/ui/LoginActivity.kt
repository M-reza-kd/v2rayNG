package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLoginBinding
import com.v2ray.ang.dto.UserCredentials
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AuthManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LoginActivity : BaseActivity() {
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    companion object {
        private const val BASE_URL = "https://sub.play2pia.com"
        
        // Channel name to path mapping
        private val CHANNEL_MAP = mapOf(
            "swift" to "Swift",
            "cyber" to "CyberTunnel"
        )
        
        /**
         * Converts a channel name to its corresponding base URL.
         * @param channelName The channel name (case-insensitive)
         * @return The full base URL, or null if channel is invalid
         */
        fun getBaseUrlForChannel(channelName: String): String? {
            val normalizedChannel = channelName.trim().lowercase(Locale.ROOT)
            val path = CHANNEL_MAP[normalizedChannel] ?: return null
            return "$BASE_URL/$path"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.login_title)

        // Pre-fill with existing credentials if available
        val existingCredentials = MmkvManager.getUserCredentials()
        if (existingCredentials != null) {
            // Try to extract channel name from server URL
            val channelName = extractChannelFromUrl(existingCredentials.serverUrl)
            binding.etChannelName.setText(channelName)
            binding.etSubscriptionId.setText(existingCredentials.subscriptionId)
        }

        binding.btnLogin.setOnClickListener { performLogin() }
    }

    /**
     * Extracts the channel name from a server URL.
     * For example: "https://sub.play2pia.com/Swift" -> "swift"
     */
    private fun extractChannelFromUrl(serverUrl: String): String {
        return when {
            serverUrl.contains("/Swift", ignoreCase = true) -> "swift"
            serverUrl.contains("/CyberTunnel", ignoreCase = true) -> "cyber"
            else -> ""
        }
    }

    private fun performLogin() {
        val channelName = binding.etChannelName.text.toString().trim()
        val subscriptionId = binding.etSubscriptionId.text.toString().trim()

        // Validate inputs
        if (TextUtils.isEmpty(channelName) || TextUtils.isEmpty(subscriptionId)) {
            toast(R.string.login_error_empty_fields)
            return
        }

        // Get base URL for channel
        val serverUrl = getBaseUrlForChannel(channelName)
        if (serverUrl == null) {
            toast(R.string.login_error_invalid_channel)
            return
        }

        // Show loading
        showLoading(true)
        binding.tvError.visibility = View.GONE

        // Perform authentication
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Test connection by attempting to fetch subscription
                val fullUrl = AuthManager.buildSubscriptionUrl(serverUrl, subscriptionId)
                val response = HttpUtil.getUrlContentWithUserAgent(fullUrl, null, 15000, 0)

                if (response.isNotEmpty()) {
                    // Save credentials
                    val credentials =
                            UserCredentials(
                                    subscriptionId = subscriptionId,
                                    serverUrl = serverUrl,
                                    isLoggedIn = true,
                                    loginTime = System.currentTimeMillis()
                            )
                    MmkvManager.saveUserCredentials(credentials)

                    // Auto-fetch subscription configs
                    val configCount = AngConfigManager.autoFetchSubscriptionWithAuth()

                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        if (configCount > 0) {
                            toastSuccess(R.string.login_success)
                        } else {
                            toast("Login successful but no configs were fetched")
                        }
                        // Navigate to main activity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        showError(getString(R.string.login_error_connection))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError(getString(R.string.login_error_connection))
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.pbLoading.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.etChannelName.isEnabled = !show
        binding.etSubscriptionId.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
