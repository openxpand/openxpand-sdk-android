# OpenXpand SDK — Manual de integración

SDK Android para autenticación de suscriptores móviles mediante el protocolo OAuth2 con PKCE (RFC 7636). Soporta múltiples métodos de identificación del suscriptor y permite elegir si el token exchange se hace desde la app o desde un backend.

## Requisitos

- Android `minSdk 26` (Android 8.0+)
- Kotlin coroutines

## Instalación

### 1. Agregar el repositorio JitPack

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

### 2. Agregar la dependencia

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.openxpand:openxpand-sdk:VERSION")
}
```

Reemplazar `VERSION` por el tag del release deseado (ej. `v1.0.0`) o por un commit hash.

## Configuración

Crear una instancia de `OpenXpandConfig` con los datos del cliente OAuth2:

```kotlin
val config = OpenXpandConfig(
    clientId = "mi-client-id",
    tenant = "telecom",                  // realm/tenant del servidor de auth
    redirectUri = "https://mi-app.com/callback"
)
```

> **Nota:** El SDK no maneja `client_secret`. Si el cliente OAuth2 es confidential,
> el secret debe manejarse exclusivamente en el backend que realiza el token exchange.

### Parámetros opcionales

| Parámetro | Default | Descripción |
|-----------|---------|-------------|
| `clientSecret` | `""` | Solo si el cliente es confidential y el token exchange lo hace código que puede usar el secret. |
| `baseGatewayUrl` | `https://opengw.openxpand.com` | Base del gateway; sirve para documentar el path del **POST /token** (`tokenEndpoint`). Las peticiones **GET /auth** usan URLs fijas en el SDK (`OpenXpandDefaults`). |
| `otpSendEndpoint` | `""` | URL para enviar el OTP por SMS. |
| `otpValidateEndpoint` | `""` | URL para validar el código OTP. |

### URLs fijas del SDK (`OpenXpandDefaults`)

No se configuran desde la app:

- **Auth HTTPS (IP+puerto):** `https://auth.openxpand.com`
- **Auth HTTP (celular / header enrichment):** `http://opengw.openxpand.com`

### Endpoints derivados

- **Auth endpoint (silent / IP+puerto):** `{BASE_AUTH_HTTPS}/auth/realms/{tenant}/protocol/openid-connect/auth`
- **Cellular auth endpoint:** `{BASE_CELLULAR_HTTP}/auth/realms/{tenant}/protocol/openid-connect/auth`
- **Token endpoint (referencia):** `{baseGatewayUrl}/auth/realms/{tenant}/protocol/openid-connect/token` — el intercambio de código lo implementa tu app o backend.

## Inicialización

```kotlin
val auth = OpenXpandAuth(
    context = applicationContext,
    config = config
)
```

## Métodos de autenticación

El SDK ofrece 3 métodos para identificar al suscriptor:

### 1. IP + Puerto (HTTPS)

Envía un request HTTPS al servidor de auth. El servidor identifica al suscriptor por la IP y puerto de origen asignados por el operador móvil.

### 2. Cellular — Header Enrichment (HTTP)

Fuerza el request por la red celular (incluso si hay WiFi). La telco intercepta el tráfico HTTP plano e inyecta un header encriptado que el servidor descifra para identificar al suscriptor.

### 3. OTP por SMS

Envía un código OTP por SMS al número del suscriptor y lo valida automáticamente usando la SMS Retriever API. Ideal para dispositivos conectados por WiFi.

---

## Uso recomendado: autorización en el SDK, token fuera del SDK

El SDK **solo** realiza el **GET** al endpoint de autorización (IP+puerto, cellular o ambos con `authorize()`). El **POST** al endpoint `/token` debe implementarlo tu app o tu backend: el SDK **no** incluye intercambio de código por tokens para el flujo OpenID estándar.

### Paso 1 — Obtener el authorization code (SDK)

Los métodos `authorize*` obtienen el code y el PKCE `code_verifier`:

```kotlin
// IP + Puerto
val authzResult = auth.authorizeViaIpPort()

// Cellular
val authzResult = auth.authorizeViaCellular()
```

```kotlin
when (authzResult) {
    is AuthorizationResult.Success -> {
        val code = authzResult.authorizationCode
        val codeVerifier = authzResult.codeVerifier
        // Enviar code + codeVerifier al backend
    }
    is AuthorizationResult.Error -> {
        val message = authzResult.message
    }
}
```

### Paso 2 — Intercambiar el code por tokens (tu código)

Desde la app (OkHttp, Retrofit, etc.) o desde un backend: POST al token endpoint con `grant_type=authorization_code`, `code`, `client_id`, `redirect_uri`, `code_verifier` y, si aplica, `client_secret`.

Ejemplo si el intercambio lo hace un **backend**:

El backend recibe `code` y `code_verifier`, y hace el POST al token endpoint:

```
POST {baseGatewayUrl}/auth/realms/{tenant}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authorization_code}
&client_id={client_id}
&client_secret={client_secret}    ← solo el backend lo conoce
&redirect_uri={redirect_uri}
&code_verifier={code_verifier}
```

---

## OTP por SMS (full-flow en el SDK)

El flujo OTP usa endpoints configurados en `OpenXpandConfig` (`otpSendEndpoint` / `otpValidateEndpoint`) y sigue siendo un método de conveniencia que devuelve `AuthResult` con tokens:

```kotlin
val result = auth.authenticateViaOtp("+5411...")
```

---

## Resumen de la API

### `OpenXpandAuth`

| Método | Retorna | Descripción |
|--------|---------|-------------|
| `authorize()` | `AuthorizationResult` | Intenta cellular y, si falla, IP+puerto. |
| `authorizeViaIpPort()` | `AuthorizationResult` | Code vía IP+puerto (HTTPS). |
| `authorizeViaCellular()` | `AuthorizationResult` | Code vía header enrichment (HTTP). |
| `authenticateViaOtp(phoneNumber)` | `AuthResult` | OTP por SMS (flujo propio del SDK). |

### Tipos de resultado

**`AuthorizationResult`** (solo code):
- `Success(authorizationCode: String, codeVerifier: String)`
- `Error(message: String, cause: Throwable?)`

**`AuthResult`** (tokens):
- `Success(accessToken: String, tokenType: String, expiresIn: Long, refreshToken: String?)`
- `Error(message: String, cause: Throwable?)`

---

## Permisos

El SDK declara los siguientes permisos en su manifest (se fusionan automáticamente):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

- `INTERNET` — para las peticiones HTTP/HTTPS.
- `ACCESS_NETWORK_STATE` — para detectar el tipo de red (WiFi/móvil).
- `CHANGE_NETWORK_STATE` — para forzar requests por la red celular.

## Seguridad de red

El SDK incluye un `network_security_config.xml` que permite tráfico cleartext (HTTP) hacia `*.openxpand.com`, necesario para el método de header enrichment celular. Esta configuración se fusiona automáticamente con el manifest de la app.

## Dependencias

| Librería | Versión | Uso |
|----------|---------|-----|
| OkHttp | 4.12.0 | HTTP client |
| Kotlin Coroutines Android | 1.7.3 | Async/suspend |
| Play Services Auth API Phone | 18.0.2 | SMS Retriever API (OTP) |
