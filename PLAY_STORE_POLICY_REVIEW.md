# Play Store Policy Compliance Review

## Executive Summary

This document outlines potential Play Store policy compliance issues found in the v2rayNG application. Some issues require immediate attention before submission or may cause rejection.

---

## 🔴 CRITICAL ISSUES (Must Fix)

### 1. QUERY_ALL_PACKAGES Permission

**Status:** ⚠️ **REQUIRES JUSTIFICATION**

**Location:** `V2rayNG/app/src/main/AndroidManifest.xml:32`

**Issue:**

```xml
<uses-permission
    android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission" />
```

**Problem:**

-   `QUERY_ALL_PACKAGES` is a restricted permission that requires explicit approval from Google Play
-   Google Play restricts this permission to specific use cases
-   Apps using this permission must provide a justification form during submission

**Current Usage:**
The app uses this permission in:

-   `AppManagerUtil.kt` - To list installed packages for per-app proxy functionality
-   `PerAppProxyActivity.kt` - To allow users to select which apps should use the VPN/proxy

**Required Action:**

1. **Submit a permission declaration form** in Google Play Console explaining:

    - The permission is necessary for the core VPN functionality (per-app proxy)
    - Users explicitly choose which apps to route through the VPN
    - The permission is not used for tracking or advertising purposes

2. **Alternative approach:** Consider using `<queries>` declarations in AndroidManifest.xml for Android 11+ to query specific packages instead of all packages, if possible.

**Play Store Policy Reference:**

-   [Restricted Permissions](https://support.google.com/googleplay/android-developer/answer/9888170)

---

### 2. Privacy Policy Language

**Status:** ⚠️ **NEEDS ATTENTION**

**Location:** `CR.md`

**Issue:**

-   Privacy policy is currently in Chinese only
-   Play Store requires privacy policy to be accessible and in the language of your target markets

**Current Privacy Policy URL:**

-   `https://raw.githubusercontent.com/2dust/v2rayNG/master/CR.md`

**Required Action:**

1. Provide an English version of the privacy policy
2. Ensure the privacy policy is accessible via a stable URL
3. Link the privacy policy in:
    - Google Play Console (Store listing → Privacy policy)
    - App's About screen (already implemented in `AboutActivity.kt`)

**Play Store Policy Reference:**

-   [User Data Policy](https://support.google.com/googleplay/android-developer/answer/10787469)

---

## 🟡 WARNINGS (Should Address)

### 3. Cleartext Traffic Enabled

**Status:** ⚠️ **DOCUMENTATION NEEDED**

**Location:** `V2rayNG/app/src/main/AndroidManifest.xml:57`

**Issue:**

```xml
android:usesCleartextTraffic="true"
```

**Problem:**

-   Allows HTTP (non-HTTPS) network traffic
-   Generally discouraged but may be necessary for VPN functionality
-   Should be documented why it's needed

**Current Justification:**

-   VPN apps may need to connect to HTTP endpoints for configuration
-   User-provided server configurations may use HTTP

**Required Action:**

-   Document in privacy policy that cleartext traffic is only used for:
    -   User-configured server connections
    -   Not for transmitting user data to the app developer
-   Consider restricting cleartext traffic to specific domains if possible

---

### 4. Network Security Config - User Certificates

**Status:** ⚠️ **ACCEPTABLE BUT DOCUMENT**

**Location:** `V2rayNG/app/src/main/res/xml/network_security_config.xml:7-8`

**Issue:**

```xml
<certificates
    src="user"
    tools:ignore="AcceptsUserCertificates" />
```

**Problem:**

-   Accepts user-installed certificates
-   Necessary for VPN functionality (users may install custom CA certificates)
-   Could be a security concern if not properly documented

**Required Action:**

-   Document in privacy policy that user certificates are accepted for VPN functionality
-   Ensure the app doesn't silently install certificates without user knowledge

---

### 5. Application ID vs Namespace Mismatch

**Status:** ℹ️ **INFORMATIONAL**

**Location:** `V2rayNG/app/build.gradle.kts:13`

**Issue:**

-   Application ID: `com.korrekhar.vpn`
-   Namespace: `com.v2ray.ang`

**Note:**

-   This appears intentional (rebranding)
-   Ensure consistency across all app metadata
-   Verify package name matches in Play Console

---

## ✅ COMPLIANT AREAS

### 6. VPN Service Declaration

**Status:** ✅ **COMPLIANT**

-   Properly declared with `BIND_VPN_SERVICE` permission
-   Uses `FOREGROUND_SERVICE_SPECIAL_USE` with proper subtype (`vpn`)
-   Includes `SUPPORTS_ALWAYS_ON` metadata
-   All VPN-related permissions are standard and acceptable

---

### 7. No Analytics/Tracking SDKs

**Status:** ✅ **GOOD**

**Verified:**

-   No Firebase Analytics
-   No Google Analytics
-   No Crashlytics
-   No Facebook SDK
-   No Adjust/AppsFlyer
-   No other tracking libraries found

This aligns with the privacy policy statement that the app doesn't collect user data.

---

### 8. Privacy Policy Link in App

**Status:** ✅ **IMPLEMENTED**

-   Privacy policy link is accessible in `AboutActivity.kt`
-   Links to: `https://raw.githubusercontent.com/2dust/v2rayNG/master/CR.md`
-   User can access it from the app's About screen

---

### 9. Permissions Usage

**Status:** ✅ **MOSTLY COMPLIANT**

**Standard Permissions (OK):**

-   `INTERNET` - Required for VPN
-   `ACCESS_NETWORK_STATE` - Standard networking permission
-   `CHANGE_NETWORK_STATE` - Required for VPN
-   `CAMERA` - Used for QR code scanning (with runtime permission)
-   `FOREGROUND_SERVICE` - Required for VPN service
-   `POST_NOTIFICATIONS` - Standard notification permission
-   `READ_MEDIA_IMAGES` - Used for QR code import from images
-   `RECEIVE_BOOT_COMPLETED` - For auto-start VPN (user configurable)

**All permissions appear to be used for legitimate VPN functionality.**

---

## 📋 RECOMMENDATIONS

### Before Submission:

1. **Submit QUERY_ALL_PACKAGES Declaration:**

    - Go to Google Play Console → App content → Sensitive permissions
    - Fill out the declaration form explaining per-app proxy functionality
    - Wait for approval before publishing

2. **Create English Privacy Policy:**

    - Translate `CR.md` to English
    - Host it at a stable URL
    - Update Play Console with the URL

3. **Review App Description:**

    - Ensure description clearly explains VPN functionality
    - Mention that it's a VPN client (not a VPN service provider)
    - Clarify that users need to provide their own server configurations

4. **Content Rating:**

    - VPN apps typically get "Everyone" or "Teen" rating
    - Be prepared to answer questions about content filtering capabilities

5. **Target Audience:**
    - Ensure app description doesn't promote circumventing geo-restrictions for copyrighted content
    - Focus on privacy and security benefits

---

## 🔍 ADDITIONAL CHECKS TO PERFORM

### Before Publishing:

-   [ ] Test app with Play Store's pre-launch report
-   [ ] Verify all declared permissions are actually used
-   [ ] Ensure privacy policy URL is accessible and returns 200 OK
-   [ ] Check that app doesn't violate any country-specific restrictions
-   [ ] Verify app doesn't contain any malware or suspicious code
-   [ ] Ensure app follows [VPN Apps Policy](https://support.google.com/googleplay/android-developer/answer/9888170#vpn)
-   [ ] Test on multiple Android versions (minSdk 21 = Android 5.0+)

---

## 📚 RELEVANT PLAY STORE POLICIES

1. [User Data Policy](https://support.google.com/googleplay/android-developer/answer/10787469)
2. [Restricted Permissions](https://support.google.com/googleplay/android-developer/answer/9888170)
3. [VPN Apps Policy](https://support.google.com/googleplay/android-developer/answer/9888170#vpn)
4. [Privacy Policy Requirements](https://support.google.com/googleplay/android-developer/answer/10787469#privacy)

---

## ⚠️ RISK ASSESSMENT

**Overall Risk Level:** 🟡 **MEDIUM**

**Primary Risks:**

1. **QUERY_ALL_PACKAGES rejection** - High risk if not properly justified
2. **Privacy policy language** - Medium risk if not in English
3. **VPN app review** - Medium risk (VPN apps face stricter review)

**Mitigation:**

-   Submit proper justification for QUERY_ALL_PACKAGES
-   Provide English privacy policy
-   Ensure app description is clear about functionality
-   Be prepared for extended review process (VPN apps often take longer)

---

## 📝 SUMMARY

**Must Fix Before Submission:**

1. ✅ Submit QUERY_ALL_PACKAGES permission declaration
2. ✅ Provide English privacy policy

**Should Address:**

1. Document cleartext traffic usage
2. Document user certificate acceptance

**Already Compliant:**

-   VPN service properly declared
-   No tracking/analytics
-   Privacy policy link in app
-   Standard permissions properly used

**Estimated Time to Fix:** 2-4 hours
**Review Time:** Expect 1-2 weeks for initial review (VPN apps)

---

_Last Updated: Based on current codebase analysis_
_Review Date: Check Play Store policies regularly as they change_
