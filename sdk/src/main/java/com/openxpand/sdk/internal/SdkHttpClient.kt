package com.openxpand.sdk.internal

import com.openxpand.sdk.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal object SdkHttpClient {

    @Volatile
    var forceHttpLogging: Boolean = false

    private val loggedBanner = AtomicBoolean(false)

    /** Evita filtrar `client_secret` en cuerpos form-url-encoded logueados en debug. */
    private val redactClientSecret = Regex("(client_secret=)[^&\\s]+")

    private fun loggingEnabled(): Boolean = BuildConfig.DEBUG || forceHttpLogging

    private fun redactForLog(message: String): String =
        message.replace(redactClientSecret) { "${it.groupValues[1]}██" }

    fun newBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        if (loggingEnabled()) {
            if (loggedBanner.compareAndSet(false, true)) {
                HttpLog.println("OpenXpand SDK HTTP logging ON — adb: adb logcat '*:S' 'OpenXpandHttp:I'")
            }
            val logging = HttpLoggingInterceptor { message -> HttpLog.println(redactForLog(message)) }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        return builder
    }

    fun create(): OkHttpClient = newBuilder().build()
}
