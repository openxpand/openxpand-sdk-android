package com.openxpand.sdk.consent

import android.app.Activity
import android.app.AlertDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal object OperatorTokenConsentDialog {

    suspend fun awaitUserConsent(activity: Activity, consentText: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (activity.isFinishing || activity.isDestroyed) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            var delivered = false
            fun finish(accepted: Boolean) {
                if (delivered) return
                delivered = true
                if (continuation.isActive) {
                    continuation.resume(accepted)
                }
            }
            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle("Share phone number")
                    .setMessage(consentText)
                    .setPositiveButton("Allow") { dialog, _ ->
                        dialog.dismiss()
                        finish(true)
                    }
                    .setNegativeButton("Deny") { dialog, _ ->
                        dialog.dismiss()
                        finish(false)
                    }
                    .setOnCancelListener {
                        finish(false)
                    }
                    .show()
            }
            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    if (!delivered) {
                        finish(false)
                    }
                }
            }
        }
}
