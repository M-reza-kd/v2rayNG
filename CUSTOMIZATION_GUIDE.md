# v2rayNG Subscription ID Authentication - Customization Guide

## Overview
This customization adds a subscription ID-based authentication system to v2rayNG. Users log in once with their subscription ID, and the app automatically fetches configuration data from your server.

## What Was Changed

### 1. New Files Created

#### Data Model
- **`UserCredentials.kt`** - Stores user authentication data:
  - `subscriptionId`: The user's subscription ID
  - `serverUrl`: Your API server base URL
  - `isLoggedIn`: Login status flag
  - `loginTime`: Timestamp of login

#### UI Components
- **`LoginActivity.kt`** - Authentication screen where users enter:
  - Server URL (your API endpoint)
  - Subscription ID
- **`activity_login.xml`** - Login screen layout with Material Design components

#### Business Logic
- **`AuthManager.kt`** - Handles all authentication operations:
  - Building subscription URLs
  - Checking login status
  - Logout functionality
  - Getting current subscription ID

### 2. Modified Files

#### Core Handlers
- **`MmkvManager.kt`** - Added credential storage methods:
  - `saveUserCredentials()` - Saves user credentials securely
  - `getUserCredentials()` - Retrieves saved credentials
  - `clearUserCredentials()` - Clears credentials on logout
  - `isUserLoggedIn()` - Checks if user is logged in

- **`AngConfigManager.kt`** - Added auto-fetch method:
  - `autoFetchSubscriptionWithAuth()` - Automatically fetches subscription configs using the authenticated user's credentials

#### UI Updates
- **`MainActivity.kt`** - Added authentication flow:
  - Checks login status on app startup
  - Redirects to login if not authenticated
  - Auto-fetches subscription data on startup
  - Added logout functionality in navigation drawer

- **`LoginActivity.kt`** - Registered in AndroidManifest.xml

#### Resources
- **`strings.xml`** - Added new strings for login UI
- **`menu_drawer.xml`** - Added logout menu item

## How It Works

### Authentication Flow

1. **App Launch:**
   ```
   User opens app → MainActivity checks AuthManager.isLoggedIn()
   → If not logged in → Redirect to LoginActivity
   → If logged in → Continue to main screen
   ```

2. **Login Process:**
   ```
   User enters Server URL and Subscription ID
   → App validates inputs
   → Builds subscription URL: {serverUrl}/api/subscription?token={subscriptionId}
   → Attempts to fetch configs from server
   → If successful:
     - Saves credentials to MMKV storage
     - Auto-fetches subscription configs
     - Navigates to MainActivity
   → If failed:
     - Shows error message
   ```

3. **Auto-Fetch on Startup:**
   ```
   MainActivity.onCreate() → autoFetchSubscription()
   → Calls AngConfigManager.autoFetchSubscriptionWithAuth()
   → Uses AuthManager.getSubscriptionUrl() to build URL
   → Fetches configs from: {serverUrl}/api/subscription?token={subscriptionId}
   → Parses and imports configs
   → Reloads server list
   ```

4. **Logout:**
   ```
   User selects Logout from navigation drawer
   → Confirmation dialog
   → If confirmed:
     - Stops VPN if running
     - Clears credentials via AuthManager.logout()
     - Redirects to LoginActivity
   ```

## Server API Requirements

Your server must implement this endpoint:

### Endpoint: `/api/subscription`
- **Method:** GET
- **Parameters:** 
  - `token` (query parameter) - The subscription ID
  
**Example Request:**
```
GET https://yourserver.com/api/subscription?token=user123
```

**Response Format:**
The server should return v2ray subscription data in standard format (base64 encoded server configs), for example:

```
vmess://eyJhZGQiOiIxMjcuMC4wLjEiLCJhaWQiOiIwIiwiaG9zdCI6IiIsImlkIjoiYTM0ODJlODgtNjg2YS00YTU4LTgxMjYtOTljOWRmNjRiN2JmIiwibmV0IjoidGNwIiwicGF0aCI6IiIsInBvcnQiOiIxMDA4NiIsInBzIjoidGVzdCIsInRscyI6IiIsInR5cGUiOiJub25lIiwidiI6IjIifQ==
vless://a3482e88-686a-4a58-8126-99c9df64b7bf@127.0.0.1:10086?type=tcp&security=none#test2
```

Or multiple configs separated by newlines.

## Configuration

### Setting Up Your Server URL

Users will need to enter your server's base URL in the login screen. Examples:
- `https://api.yourservice.com`
- `https://yourservice.com`
- `http://192.168.1.100:8080` (for testing)

The app automatically appends `/api/subscription?token={subscriptionId}` to this URL.

### Testing

1. **Build the app:**
   ```bash
   cd /root/v2rayNG
   ./gradlew assembleDebug
   ```

2. **Test Login Flow:**
   - Open the app
   - Enter your server URL
   - Enter a valid subscription ID
   - Verify configs are fetched and displayed

3. **Test Auto-Fetch:**
   - Close and reopen the app
   - Verify configs are automatically refreshed

4. **Test Logout:**
   - Open navigation drawer
   - Select "Logout"
   - Confirm logout
   - Verify redirect to login screen

## Security Considerations

1. **HTTPS Required:** For production, your server should use HTTPS
2. **Credential Storage:** Credentials are stored in MMKV (encrypted key-value storage)
3. **Token Security:** The subscription ID is sent as a URL parameter

## Customization Options

### Change API Endpoint Path
Edit `AuthManager.kt`:
```kotlin
fun buildSubscriptionUrl(serverUrl: String, subscriptionId: String): String {
    val baseUrl = serverUrl.trimEnd('/')
    // Change this line to match your API structure
    return "$baseUrl/api/subscription?token=$subscriptionId"
}
```

### Add Additional Fields
To add password or other fields:

1. Update `UserCredentials.kt`:
```kotlin
data class UserCredentials(
    var subscriptionId: String = "",
    var password: String = "",  // Add this
    var serverUrl: String = "",
    var isLoggedIn: Boolean = false,
    val loginTime: Long = System.currentTimeMillis()
)
```

2. Update `activity_login.xml` to add password field

3. Update `LoginActivity.kt` to handle the password

4. Update `AuthManager.kt` to include password in API request

### Auto-Update Interval
The app can automatically refresh configs. Users can enable this in subscription settings.

## Troubleshooting

### Login Fails
- Check server URL is correct
- Verify subscription ID is valid
- Check server is accessible from the device
- Review logs: `adb logcat | grep v2rayNG`

### Configs Not Loading
- Verify server returns data in correct format
- Check network connectivity
- Review server logs for errors

### Build Errors
- Ensure all dependencies are installed
- Clean and rebuild: `./gradlew clean build`

## Files Modified Summary

**New Files:**
- `V2rayNG/app/src/main/java/com/v2ray/ang/dto/UserCredentials.kt`
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/LoginActivity.kt`
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AuthManager.kt`
- `V2rayNG/app/src/main/res/layout/activity_login.xml`

**Modified Files:**
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/MmkvManager.kt`
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt`
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt`
- `V2rayNG/app/src/main/res/values/strings.xml`
- `V2rayNG/app/src/main/res/menu/menu_drawer.xml`
- `V2rayNG/app/src/main/AndroidManifest.xml`

## Next Steps

1. **Update your server** to implement the `/api/subscription?token=X` endpoint
2. **Test the implementation** with real subscription IDs
3. **Customize the UI** (logos, colors, etc.) as needed
4. **Build and distribute** your customized app

## Support

For questions or issues with this customization, refer to the code comments in each modified file for detailed explanations of the implementation.

