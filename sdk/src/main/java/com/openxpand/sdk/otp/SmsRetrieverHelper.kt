package com.openxpand.sdk.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SmsRetrieverHelper(private val context: Context) {

    suspend fun waitForOtpCode(timeoutMs: Long = 120_000L): String =
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val client = SmsRetriever.getClient(context)
                val task = client.startSmsRetriever()

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (SmsRetriever.SMS_RETRIEVED_ACTION != intent.action) return

                        val extras = intent.extras ?: return
                        val status = BundleCompat.getParcelable(
                            extras,
                            SmsRetriever.EXTRA_STATUS,
                            Status::class.java
                        ) ?: return

                        when (status.statusCode) {
                            CommonStatusCodes.SUCCESS -> {
                                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: ""
                                val otpCode = extractOtp(message)
                                context.unregisterReceiver(this)
                                if (otpCode != null) {
                                    continuation.resume(otpCode)
                                } else {
                                    continuation.resumeWithException(
                                        OtpException("Could not extract OTP from SMS: $message")
                                    )
                                }
                            }
                            CommonStatusCodes.TIMEOUT -> {
                                context.unregisterReceiver(this)
                                continuation.resumeWithException(
                                    OtpException("SMS Retriever timed out")
                                )
                            }
                        }
                    }
                }

                val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                continuation.invokeOnCancellation {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {}
                }

                task.addOnFailureListener { e ->
                    continuation.resumeWithException(
                        OtpException("Failed to start SMS Retriever", e)
                    )
                }
            }
        }

    private fun extractOtp(message: String): String? {
        val pattern = Regex("\\b(\\d{4,8})\\b")
        return pattern.find(message)?.groupValues?.get(1)
    }
}

class OtpException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
