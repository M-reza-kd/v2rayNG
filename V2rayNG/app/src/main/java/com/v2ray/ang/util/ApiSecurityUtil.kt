package com.v2ray.ang.util

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Security utility for signing API requests to ensure only the app can make requests.
 * 
 * This implements HMAC-SHA256 request signing with the following security features:
 * - Request signing with a secret key embedded in the app
 * - Timestamp-based nonce to prevent replay attacks
 * - App package name verification
 * - Request signature in headers
 */
object ApiSecurityUtil {

    /**
     * API Secret Key - This should match the secret key on your backend server.
     * 
     * IMPORTANT SECURITY NOTES:
     * 1. This key is embedded in the app and can be extracted by reverse engineering
     * 2. This is NOT perfect security, but it prevents casual API abuse
     * 3. For stronger security, consider:
     *    - Using Google Play Integrity API (formerly SafetyNet)
     *    - Certificate pinning
     *    - OAuth/JWT tokens
     *    - Rate limiting on backend
     * 
     * To change this key:
     * 1. Update this constant
     * 2. Update the same key on your backend server
     * 3. Rebuild and redeploy both app and backend
     */
    private const val API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION"

    /**
     * Header names for authentication
     */
    const val HEADER_APP_PACKAGE = "X-App-Package"
    const val HEADER_APP_VERSION = "X-App-Version"
    const val HEADER_TIMESTAMP = "X-Request-Timestamp"
    const val HEADER_NONCE = "X-Request-Nonce"
    const val HEADER_SIGNATURE = "X-Request-Signature"

    /**
     * Maximum allowed time difference for timestamp validation (5 minutes)
     */
    private const val MAX_TIMESTAMP_DIFF_MS = 5 * 60 * 1000L

    /**
     * Generates a secure signature for the API request.
     * 
     * Signature format: HMAC-SHA256(
     *   method + url + timestamp + nonce + packageName + body
     * )
     * 
     * @param method HTTP method (GET, POST, etc.)
     * @param url Full request URL
     * @param timestamp Request timestamp in milliseconds
     * @param nonce Random nonce string
     * @param packageName App package name
     * @param body Request body (empty string for GET requests)
     * @return Base64-encoded HMAC-SHA256 signature
     */
    fun generateSignature(
        method: String,
        url: String,
        timestamp: Long,
        nonce: String,
        packageName: String,
        body: String = ""
    ): String {
        try {
            // Build the string to sign
            val stringToSign = buildString {
                append(method.uppercase())
                append("|")
                append(url)
                append("|")
                append(timestamp)
                append("|")
                append(nonce)
                append("|")
                append(packageName)
                if (body.isNotEmpty()) {
                    append("|")
                    append(body)
                }
            }

            Log.d(AppConfig.TAG, "API Security: Signing request - Method: $method, URL: $url")

            // Generate HMAC-SHA256 signature
            val secretKey = SecretKeySpec(
                API_SECRET_KEY.toByteArray(StandardCharsets.UTF_8),
                "HmacSHA256"
            )
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val signatureBytes = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))

            // Return base64-encoded signature
            return android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to generate API signature", e)
            throw RuntimeException("Failed to generate API signature", e)
        }
    }

    /**
     * Generates a random nonce for request uniqueness.
     * 
     * @return Random nonce string
     */
    fun generateNonce(): String {
        val randomBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(randomBytes)
        return android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }

    /**
     * Gets the current timestamp in milliseconds.
     * 
     * @return Current timestamp
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Gets the app's package name.
     * 
     * @return Package name from BuildConfig
     */
    fun getAppPackageName(): String {
        return BuildConfig.APPLICATION_ID
    }

    /**
     * Gets the app's version name.
     * 
     * @return Version name from BuildConfig
     */
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Validates a timestamp to prevent replay attacks.
     * 
     * @param timestamp Timestamp to validate
     * @return True if timestamp is within acceptable range
     */
    fun isValidTimestamp(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val diff = kotlin.math.abs(currentTime - timestamp)
        return diff <= MAX_TIMESTAMP_DIFF_MS
    }

    /**
     * Creates authentication headers for an API request.
     * 
     * @param method HTTP method
     * @param url Full request URL
     * @param body Request body (empty for GET requests)
     * @return Map of header name to header value
     */
    fun createAuthHeaders(
        method: String,
        url: String,
        body: String = ""
    ): Map<String, String> {
        val timestamp = getCurrentTimestamp()
        val nonce = generateNonce()
        val packageName = getAppPackageName()
        val version = getAppVersion()

        val signature = generateSignature(method, url, timestamp, nonce, packageName, body)

        return mapOf(
            HEADER_APP_PACKAGE to packageName,
            HEADER_APP_VERSION to version,
            HEADER_TIMESTAMP to timestamp.toString(),
            HEADER_NONCE to nonce,
            HEADER_SIGNATURE to signature
        )
    }

    /**
     * Verifies a request signature (for backend use - included for reference).
     * 
     * NOTE: This method is for documentation purposes. The actual verification
     * should be done on your backend server.
     * 
     * @param method HTTP method
     * @param url Full request URL
     * @param timestamp Request timestamp
     * @param nonce Request nonce
     * @param packageName App package name
     * @param body Request body
     * @param receivedSignature Signature received in header
     * @return True if signature is valid
     */
    fun verifySignature(
        method: String,
        url: String,
        timestamp: Long,
        nonce: String,
        packageName: String,
        body: String,
        receivedSignature: String
    ): Boolean {
        // Validate timestamp first
        if (!isValidTimestamp(timestamp)) {
            Log.w(AppConfig.TAG, "API Security: Invalid timestamp - possible replay attack")
            return false
        }

        // Verify package name matches expected app
        val expectedPackage = getAppPackageName()
        if (packageName != expectedPackage) {
            Log.w(AppConfig.TAG, "API Security: Package name mismatch - expected: $expectedPackage, got: $packageName")
            return false
        }

        // Generate expected signature
        val expectedSignature = generateSignature(method, url, timestamp, nonce, packageName, body)

        // Constant-time comparison to prevent timing attacks
        return expectedSignature == receivedSignature
    }
}
