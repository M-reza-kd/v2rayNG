# Backend API Security Implementation Guide

This guide explains how to secure your backend API so only the app can make requests to get subscription IDs.

## Overview

The app now includes request signing using HMAC-SHA256. All requests to your backend API include authentication headers that must be verified on the server side.

## Security Features

1. **HMAC-SHA256 Request Signing** - Each request is signed with a secret key
2. **Timestamp Validation** - Prevents replay attacks (5-minute window)
3. **Nonce (Random Number)** - Ensures request uniqueness
4. **App Package Verification** - Verifies requests come from the correct app
5. **Request Signature** - Cryptographically verifies request authenticity

## Request Headers

The app automatically adds these headers to authenticated requests:

-   `X-App-Package`: App package name (e.g., `com.korrekhar.vpn`)
-   `X-App-Version`: App version name (e.g., `1.10.30`)
-   `X-Request-Timestamp`: Unix timestamp in milliseconds
-   `X-Request-Nonce`: Random nonce string (base64, URL-safe)
-   `X-Request-Signature`: HMAC-SHA256 signature (base64-encoded)

## Signature Generation

The signature is calculated as:

```
HMAC-SHA256(
  METHOD + "|" + URL + "|" + TIMESTAMP + "|" + NONCE + "|" + PACKAGE_NAME + "|" + BODY
)
```

Where:

-   `METHOD`: HTTP method (e.g., "GET")
-   `URL`: Full request URL
-   `TIMESTAMP`: Request timestamp in milliseconds
-   `NONCE`: Random nonce string
-   `PACKAGE_NAME`: App package name
-   `BODY`: Request body (empty string for GET requests)

## Backend Implementation

### Step 1: Set the Secret Key

**IMPORTANT:** You must set the same secret key in both:

1. The app: `V2rayNG/app/src/main/java/com/v2ray/ang/util/ApiSecurityUtil.kt` (line 30)
2. Your backend server

**Change the key in the app:**

```kotlin
private const val API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION"
```

Replace `YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION` with a strong, random secret key (at least 32 characters).

### Step 2: Backend Verification (Example: Python/Flask)

```python
import hmac
import hashlib
import base64
import time
from flask import request, jsonify

# Must match the key in ApiSecurityUtil.kt
API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION"
EXPECTED_PACKAGE = "com.korrekhar.vpn"  # Your app's package name
MAX_TIMESTAMP_DIFF = 5 * 60 * 1000  # 5 minutes in milliseconds

def verify_request_signature():
    """Verify the request signature from the app."""
    # Get headers
    app_package = request.headers.get('X-App-Package')
    timestamp = request.headers.get('X-Request-Timestamp')
    nonce = request.headers.get('X-Request-Nonce')
    signature = request.headers.get('X-Request-Signature')

    # Check required headers
    if not all([app_package, timestamp, nonce, signature]):
        return False, "Missing required headers"

    # Verify package name
    if app_package != EXPECTED_PACKAGE:
        return False, f"Invalid package name: {app_package}"

    # Verify timestamp (prevent replay attacks)
    try:
        timestamp_ms = int(timestamp)
        current_time_ms = int(time.time() * 1000)
        time_diff = abs(current_time_ms - timestamp_ms)

        if time_diff > MAX_TIMESTAMP_DIFF:
            return False, f"Timestamp too old or too new: {time_diff}ms difference"
    except ValueError:
        return False, "Invalid timestamp format"

    # Build the string to sign (same format as app)
    method = request.method
    url = request.url
    body = request.get_data(as_text=True) if request.method in ['POST', 'PUT', 'PATCH'] else ""

    string_to_sign = f"{method}|{url}|{timestamp}|{nonce}|{app_package}|{body}"

    # Generate expected signature
    secret_key_bytes = API_SECRET_KEY.encode('utf-8')
    mac = hmac.new(secret_key_bytes, string_to_sign.encode('utf-8'), hashlib.sha256)
    expected_signature = base64.b64encode(mac.digest()).decode('utf-8')

    # Constant-time comparison (prevent timing attacks)
    if not hmac.compare_digest(signature, expected_signature):
        return False, "Invalid signature"

    return True, "Signature verified"

@app.route('/register', methods=['GET'])
def register_device():
    """Device registration endpoint."""
    # Verify request signature
    is_valid, message = verify_request_signature()
    if not is_valid:
        return jsonify({"error": f"Authentication failed: {message}"}), 401

    # Get device ID from query parameter
    device_id = request.args.get('deviceId')
    if not device_id:
        return jsonify({"error": "Missing deviceId parameter"}), 400

    # Your registration logic here
    # Generate or retrieve subscription ID for this device
    subscription_id = generate_subscription_id(device_id)

    return jsonify({"subscriptionId": subscription_id})

@app.route('/KorreKhar/<subscription_id>', methods=['GET'])
def get_subscription(subscription_id):
    """Subscription endpoint."""
    # Verify request signature
    is_valid, message = verify_request_signature()
    if not is_valid:
        return jsonify({"error": f"Authentication failed: {message}"}), 401

    # Your subscription logic here
    # Return v2ray configs for this subscription
    configs = get_subscription_configs(subscription_id)

    return configs, 200, {'Content-Type': 'text/plain; charset=utf-8'}
```

### Step 3: Backend Verification (Example: Node.js/Express)

```javascript
const crypto = require("crypto");
const express = require("express");
const app = express();

// Must match the key in ApiSecurityUtil.kt
const API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION";
const EXPECTED_PACKAGE = "com.korrekhar.vpn";
const MAX_TIMESTAMP_DIFF = 5 * 60 * 1000; // 5 minutes

function verifyRequestSignature(req) {
    // Get headers
    const appPackage = req.headers["x-app-package"];
    const timestamp = req.headers["x-request-timestamp"];
    const nonce = req.headers["x-request-nonce"];
    const signature = req.headers["x-request-signature"];

    // Check required headers
    if (!appPackage || !timestamp || !nonce || !signature) {
        return { valid: false, message: "Missing required headers" };
    }

    // Verify package name
    if (appPackage !== EXPECTED_PACKAGE) {
        return { valid: false, message: `Invalid package name: ${appPackage}` };
    }

    // Verify timestamp
    const timestampMs = parseInt(timestamp, 10);
    const currentTimeMs = Date.now();
    const timeDiff = Math.abs(currentTimeMs - timestampMs);

    if (isNaN(timestampMs) || timeDiff > MAX_TIMESTAMP_DIFF) {
        return {
            valid: false,
            message: `Timestamp invalid or expired: ${timeDiff}ms difference`,
        };
    }

    // Build string to sign
    const method = req.method;
    const url = req.protocol + "://" + req.get("host") + req.originalUrl;
    const body = req.method === "GET" ? "" : JSON.stringify(req.body);

    const stringToSign = `${method}|${url}|${timestamp}|${nonce}|${appPackage}|${body}`;

    // Generate expected signature
    const hmac = crypto.createHmac("sha256", API_SECRET_KEY);
    hmac.update(stringToSign);
    const expectedSignature = hmac.digest("base64");

    // Constant-time comparison
    if (
        !crypto.timingSafeEqual(
            Buffer.from(signature),
            Buffer.from(expectedSignature)
        )
    ) {
        return { valid: false, message: "Invalid signature" };
    }

    return { valid: true, message: "Signature verified" };
}

app.get("/register", (req, res) => {
    // Verify request signature
    const verification = verifyRequestSignature(req);
    if (!verification.valid) {
        return res
            .status(401)
            .json({ error: `Authentication failed: ${verification.message}` });
    }

    // Get device ID
    const deviceId = req.query.deviceId;
    if (!deviceId) {
        return res.status(400).json({ error: "Missing deviceId parameter" });
    }

    // Your registration logic
    const subscriptionId = generateSubscriptionId(deviceId);

    res.json({ subscriptionId });
});

app.get("/KorreKhar/:subscriptionId", (req, res) => {
    // Verify request signature
    const verification = verifyRequestSignature(req);
    if (!verification.valid) {
        return res
            .status(401)
            .json({ error: `Authentication failed: ${verification.message}` });
    }

    const subscriptionId = req.params.subscriptionId;

    // Your subscription logic
    const configs = getSubscriptionConfigs(subscriptionId);

    res.set("Content-Type", "text/plain; charset=utf-8");
    res.send(configs);
});
```

### Step 4: Backend Verification (Example: Go)

```go
package main

import (
    "crypto/hmac"
    "crypto/sha256"
    "encoding/base64"
    "fmt"
    "net/http"
    "strconv"
    "strings"
    "time"
)

// Must match the key in ApiSecurityUtil.kt
const API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION"
const EXPECTED_PACKAGE = "com.korrekhar.vpn"
const MAX_TIMESTAMP_DIFF = 5 * 60 * 1000 // 5 minutes

func verifyRequestSignature(r *http.Request) (bool, string) {
    // Get headers
    appPackage := r.Header.Get("X-App-Package")
    timestamp := r.Header.Get("X-Request-Timestamp")
    nonce := r.Header.Get("X-Request-Nonce")
    signature := r.Header.Get("X-Request-Signature")

    // Check required headers
    if appPackage == "" || timestamp == "" || nonce == "" || signature == "" {
        return false, "Missing required headers"
    }

    // Verify package name
    if appPackage != EXPECTED_PACKAGE {
        return false, fmt.Sprintf("Invalid package name: %s", appPackage)
    }

    // Verify timestamp
    timestampMs, err := strconv.ParseInt(timestamp, 10, 64)
    if err != nil {
        return false, "Invalid timestamp format"
    }

    currentTimeMs := time.Now().UnixNano() / int64(time.Millisecond)
    timeDiff := currentTimeMs - timestampMs
    if timeDiff < 0 {
        timeDiff = -timeDiff
    }

    if timeDiff > MAX_TIMESTAMP_DIFF {
        return false, fmt.Sprintf("Timestamp expired: %dms difference", timeDiff)
    }

    // Build string to sign
    method := r.Method
    url := r.URL.String()
    body := "" // For GET requests, body is empty

    stringToSign := fmt.Sprintf("%s|%s|%s|%s|%s|%s", method, url, timestamp, nonce, appPackage, body)

    // Generate expected signature
    mac := hmac.New(sha256.New, []byte(API_SECRET_KEY))
    mac.Write([]byte(stringToSign))
    expectedSignature := base64.StdEncoding.EncodeToString(mac.Sum(nil))

    // Constant-time comparison
    if !hmac.Equal([]byte(signature), []byte(expectedSignature)) {
        return false, "Invalid signature"
    }

    return true, "Signature verified"
}

func registerHandler(w http.ResponseWriter, r *http.Request) {
    // Verify request signature
    isValid, message := verifyRequestSignature(r)
    if !isValid {
        http.Error(w, fmt.Sprintf("Authentication failed: %s", message), http.StatusUnauthorized)
        return
    }

    // Get device ID
    deviceId := r.URL.Query().Get("deviceId")
    if deviceId == "" {
        http.Error(w, "Missing deviceId parameter", http.StatusBadRequest)
        return
    }

    // Your registration logic
    subscriptionId := generateSubscriptionId(deviceId)

    w.Header().Set("Content-Type", "application/json")
    fmt.Fprintf(w, `{"subscriptionId":"%s"}`, subscriptionId)
}

func subscriptionHandler(w http.ResponseWriter, r *http.Request) {
    // Verify request signature
    isValid, message := verifyRequestSignature(r)
    if !isValid {
        http.Error(w, fmt.Sprintf("Authentication failed: %s", message), http.StatusUnauthorized)
        return
    }

    // Extract subscription ID from URL path
    pathParts := strings.Split(r.URL.Path, "/")
    subscriptionId := pathParts[len(pathParts)-1]

    // Your subscription logic
    configs := getSubscriptionConfigs(subscriptionId)

    w.Header().Set("Content-Type", "text/plain; charset=utf-8")
    w.Write([]byte(configs))
}
```

## Security Best Practices

1. **Use HTTPS** - Always use HTTPS in production to protect the secret key and signatures in transit
2. **Strong Secret Key** - Use a strong, random secret key (at least 32 characters)
3. **Rate Limiting** - Implement rate limiting on your backend to prevent abuse
4. **Nonce Tracking** - Optionally track used nonces to prevent replay attacks (within the timestamp window)
5. **Logging** - Log failed authentication attempts for monitoring
6. **Key Rotation** - Have a plan to rotate the secret key if compromised

## Testing

1. **Test with valid signature** - Verify requests with correct signatures work
2. **Test with invalid signature** - Verify requests with wrong signatures are rejected
3. **Test expired timestamp** - Verify old requests are rejected
4. **Test wrong package name** - Verify requests from other apps are rejected
5. **Test missing headers** - Verify requests without required headers are rejected

## Migration Path

1. **Update the app** - Deploy the updated app with API security
2. **Update backend** - Deploy backend with signature verification
3. **Monitor** - Watch for authentication failures
4. **Gradual rollout** - Consider a gradual rollout if you have many users

## Troubleshooting

### App can't connect to backend

-   Check that the secret key matches in both app and backend
-   Verify backend is checking headers correctly
-   Check network connectivity and firewall rules

### Signature verification fails

-   Verify the string-to-sign format matches exactly
-   Check timestamp is within 5-minute window
-   Verify package name matches expected value
-   Check secret key is identical in app and backend

### Backend returns 401 Unauthorized

-   Check all required headers are present
-   Verify signature calculation matches
-   Check timestamp is current (within 5 minutes)
-   Verify package name is correct

## Additional Security Options

For even stronger security, consider:

1. **Google Play Integrity API** - Verify app authenticity on backend
2. **Certificate Pinning** - Pin your backend's SSL certificate in the app
3. **OAuth 2.0 / JWT** - Use industry-standard authentication
4. **IP Whitelisting** - Restrict API access to specific IP ranges (if applicable)

---

**Important:** Remember to change the `API_SECRET_KEY` in `ApiSecurityUtil.kt` before deploying to production!
