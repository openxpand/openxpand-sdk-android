# openxpand-sdk-android

Collection of Android SDK modules for OpenXpand.

| Module | Description |
|--------|-------------|
| `openxpand-android-sdk-auth` | Mobile subscriber authentication via OAuth2 + PKCE (RFC 7636) |

---

## openxpand-android-sdk-auth

Android SDK for authenticating mobile subscribers via OAuth2 with PKCE (RFC 7636). It supports multiple subscriber identification methods and lets you choose whether the token exchange is performed from the app or from a backend.

## Requirements

- Android `minSdk 26` (Android 8.0+)
- Kotlin coroutines

## Installation

### 1. Add the JitPack repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add the dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.openxpand:openxpand-sdk-android:VERSION")
}
```

Replace `VERSION` with the desired release tag (e.g. `v1.0.0`) or a commit hash.

## Configuration

Create an `OpenXpandConfig` instance with the OAuth2 client data:

```kotlin
import com.openxpand.sdk.OpenXpandConfig

val config = OpenXpandConfig(
    clientId = "my-client-id",
    tenant = "my-tenant",
    redirectUri = "https://my-app.com/callback"
)
```

> **Note:** The SDK does not handle `client_secret`. If the OAuth2 client is confidential,
> the secret must be handled exclusively by the backend that performs the token exchange.

### Optional parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `clientSecret` | `""` | Only if the client is confidential and the token exchange runs in code that can safely use the secret. |
| `baseGatewayUrl` | `https://opengw.openxpand.com` | Gateway base; used to document the **POST /token** path (`tokenEndpoint`). The **GET /auth** requests use fixed URLs defined in the SDK (`OpenXpandDefaults`). |

### Fixed SDK URLs (`OpenXpandDefaults`)

Not configurable from the app:

- **Auth HTTPS (IP + port):** `https://auth.openxpand.com`
- **Auth HTTP (cellular):** `http://opengw.openxpand.com`

### Derived endpoints

- **Auth endpoint (IP + port):** `{BASE_AUTH_HTTPS}/auth/realms/{tenant}/protocol/openid-connect/auth`
- **Cellular auth endpoint:** `{BASE_CELLULAR_HTTP}/auth/realms/{tenant}/protocol/openid-connect/auth`
- **Token endpoint (reference):** `{baseGatewayUrl}/auth/realms/{tenant}/protocol/openid-connect/token` — the code exchange is implemented by your app or backend.

## Initialization

```kotlin
import com.openxpand.sdk.OpenXpandAuth

val auth = OpenXpandAuth(
    context = applicationContext,
    config = config
)
```

## Authentication methods

The SDK offers 2 methods to identify the subscriber:

### 1. IP + Port (HTTPS)

Sends an HTTPS request to the auth server. The server identifies the subscriber by the **source IP and port** assigned by the mobile network.

### 2. Cellular (HTTP)

**Forces the request through the cellular network** (even if WiFi is available). The subscriber is identified via the cellular network itself.

---

## Recommended usage: authorization in the SDK, token outside the SDK

The SDK **only** performs the **GET** to the authorization endpoint (IP + port, cellular, or both via `authorize()`). The **POST** to the `/token` endpoint must be implemented by your app or your backend: the SDK **does not** include code-for-token exchange for the standard OpenID flow.

### Step 1 — Obtain the authorization code (SDK)

The `authorize*` methods return the code and the PKCE `code_verifier`:

```kotlin
import com.openxpand.sdk.AuthorizationResult

// IP + Port
val authzResult = auth.authorizeViaIpPort()

// Cellular
val authzResult = auth.authorizeViaCellular()

// Try cellular first, fall back to IP + port on failure
val authzResult = auth.authorize()
```

```kotlin
when (authzResult) {
    is AuthorizationResult.Success -> {
        val code = authzResult.authorizationCode
        val codeVerifier = authzResult.codeVerifier
        // Send code + codeVerifier to the backend
    }
    is AuthorizationResult.Error -> {
        val message = authzResult.message
    }
}
```

### Step 2 — Exchange the code for tokens (your code)

From the app (OkHttp, Retrofit, etc.) or from a backend: POST to the token endpoint with `grant_type=authorization_code`, `code`, `client_id`, `redirect_uri`, `code_verifier`, and `client_secret` if applicable.

Example when the exchange is done by a **backend**:

The backend receives `code` and `code_verifier`, and issues the POST to the token endpoint:

```
POST {baseGatewayUrl}/auth/realms/{tenant}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authorization_code}
&client_id={client_id}
&client_secret={client_secret}    ← only the backend knows it
&redirect_uri={redirect_uri}
&code_verifier={code_verifier}
```

### Step 3 — Verify the phone number (Number Verification API)

Once you have the `access_token`, call the **Number Verification** endpoint to validate that the phone number provided matches the one identified by the mobile network. This is the CAMARA *Number Verify* API: it confirms whether the supplied number is the device's number or not.

```
POST https://api.openxpand.com/api/camara/number-verification/v0/verify
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "phoneNumber": "INPUT NUMBER"
}
```

Expected response:

```json
{
  "devicePhoneNumberVerified": false
}
```

The `devicePhoneNumberVerified` field is `true` when the supplied `phoneNumber` matches the device's number, and `false` otherwise.

---

## API summary

### `OpenXpandAuth`

| Method | Returns | Description |
|--------|---------|-------------|
| `authorize()` | `AuthorizationResult` | Tries cellular first; on failure falls back to IP + port. |
| `authorizeViaIpPort()` | `AuthorizationResult` | Identifies the subscriber by **source IP and port** (HTTPS). |
| `authorizeViaCellular()` | `AuthorizationResult` | Identifies the subscriber via the **cellular network**. Forces the request through cellular (HTTP), even if the device is on WiFi. |

### Result types

**`AuthorizationResult`**:
- `Success(authorizationCode: String, codeVerifier: String)`
- `Error(message: String, cause: Throwable?)`

---

## Permissions

The SDK declares the following permissions in its manifest (merged automatically):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

- `INTERNET` — for HTTP/HTTPS requests.
- `ACCESS_NETWORK_STATE` — to detect the network type (WiFi/mobile).
- `CHANGE_NETWORK_STATE` — to force requests through the cellular network.

## Network security

The SDK includes a `network_security_config.xml` that allows cleartext (HTTP) traffic to `*.openxpand.com`, required by the cellular method. This configuration is merged automatically into the app manifest.

## Dependencies

| Library | Version | Usage |
|---------|---------|-------|
| OkHttp | 4.12.0 | HTTP client |
| Kotlin Coroutines Android | 1.7.3 | Async/suspend |
