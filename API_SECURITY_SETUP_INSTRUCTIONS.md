# Quick Setup: API Security for Backend

## Step 1: Change the Secret Key in the App

1. Open: `V2rayNG/app/src/main/java/com/v2ray/ang/util/ApiSecurityUtil.kt`
2. Find line 30: `private const val API_SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_IN_PRODUCTION"`
3. Replace with a strong random key (at least 32 characters)

Example:

```kotlin
private const val API_SECRET_KEY = "a7f3b9c2d4e6f8a1b3c5d7e9f0a2b4c6d8e0f2a4b6c8d0e2f4a6b8c0d2e4f6a8"
```

## Step 2: Update Your Backend

See `BACKEND_API_SECURITY_GUIDE.md` for complete backend implementation examples in:

-   Python/Flask
-   Node.js/Express
-   Go

**Key points:**

-   Use the SAME secret key as in the app
-   Verify the signature on every request
-   Check timestamp is within 5 minutes
-   Verify package name matches `com.korrekhar.vpn`

## Step 3: Test

1. Build and install the updated app
2. Test device registration
3. Test subscription fetching
4. Verify backend logs show successful signature verification

## What Changed in the App

The app now automatically adds these headers to requests to your backend:

-   `X-App-Package`: Package name
-   `X-App-Version`: App version
-   `X-Request-Timestamp`: Current timestamp
-   `X-Request-Nonce`: Random nonce
-   `X-Request-Signature`: HMAC-SHA256 signature

Your backend must verify these headers or requests will fail.

## Endpoints That Use Authentication

-   `GET /register?deviceId={uuid}` - Device registration
-   `GET /KorreKhar/{subscriptionId}` - Subscription fetching

Both endpoints now require valid signatures.
