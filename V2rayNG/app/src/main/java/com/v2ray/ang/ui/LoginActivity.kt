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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : BaseActivity() {
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.login_title)

        // Pre-fill with existing credentials if available
        val existingCredentials = MmkvManager.getUserCredentials()
        if (existingCredentials != null) {
            binding.etServerUrl.setText(existingCredentials.serverUrl)
            binding.etSubscriptionId.setText(existingCredentials.subscriptionId)
        }

        binding.btnLogin.setOnClickListener { performLogin() }
    }

    private fun performLogin() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val subscriptionId = binding.etSubscriptionId.text.toString().trim()

        // Validate inputs
        if (TextUtils.isEmpty(serverUrl) || TextUtils.isEmpty(subscriptionId)) {
            toast(R.string.login_error_empty_fields)
            return
        }

        // Validate URL format
        if (!Utils.isValidUrl(serverUrl)) {
            toast(R.string.login_error_invalid_url)
            return
        }

        // Show loading
        showLoading(true)
        binding.tvError.visibility = View.GONE

        // Perform authentication
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Test connection by attempting to fetch subscription
                val fullUrl = buildSubscriptionUrl(serverUrl, subscriptionId)
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

    private fun buildSubscriptionUrl(serverUrl: String, subscriptionId: String): String {
        // Remove trailing slash from server URL if present
        val baseUrl = serverUrl.trimEnd('/')
        // Build the subscription URL: /api/subscription?token=subscriptionId
        return "$baseUrl/api/subscription?token=$subscriptionId"
    }

    private fun showLoading(show: Boolean) {
        binding.pbLoading.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.etServerUrl.isEnabled = !show
        binding.etSubscriptionId.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
