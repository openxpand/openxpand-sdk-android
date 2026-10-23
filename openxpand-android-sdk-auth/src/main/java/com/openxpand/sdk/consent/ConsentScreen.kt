package com.openxpand.sdk.consent

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.openxpand.sdk.auth.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal sealed class ConsentPageResult {
    data class Code(val code: String) : ConsentPageResult()
    data class Failed(val message: String) : ConsentPageResult()
}

/**
 * Shows the Keycloak consent page that belongs to the authorize session
 * already started by the silent call. The temporary token must not be sent again.
 */
internal object ConsentScreen {

    suspend fun awaitCode(
        activity: Activity,
        consentUrl: String,
        cookies: List<String>,
        redirectUri: String
    ): ConsentPageResult = withContext(Dispatchers.Main) {
        if (activity.isFinishing || activity.isDestroyed) {
            return@withContext ConsentPageResult.Failed("Consent screen could not be shown")
        }
        suspendCancellableCoroutine<ConsentPageResult> { continuation ->
            val dialog = Dialog(activity)
            dialog.setContentView(R.layout.openxpand_dialog_consent)
            dialog.setCancelable(true)
            val webView = dialog.findViewById<WebView>(R.id.openxpand_consent_web)
            var delivered = false

            fun deliver(result: ConsentPageResult) {
                if (delivered) return
                delivered = true
                webView.stopLoading()
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            fun finish(url: String): Boolean {
                if (!isAuthorizationRedirect(url, redirectUri)) {
                    return false
                }
                val parsed = Uri.parse(url)
                val code = parsed.getQueryParameter("code")
                val error = parsed.getQueryParameter("error")
                if (code.isNullOrEmpty()) {
                    deliver(ConsentPageResult.Failed("Consent redirect did not include a code. Error: ${error ?: "unknown"}"))
                } else {
                    deliver(ConsentPageResult.Code(code))
                }
                return true
            }

            dialog.findViewById<android.widget.Button>(R.id.openxpand_btn_close_consent)
                .setOnClickListener { dialog.dismiss() }
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return finish(request.url.toString())
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    finish(url)
                }
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            for (cookie in cookies) {
                val value = if (consentUrl.startsWith("http://")) {
                    cookie.replace(Regex(";\\s*Secure", RegexOption.IGNORE_CASE), "")
                } else {
                    cookie
                }
                cookieManager.setCookie(consentUrl, value)
            }
            cookieManager.flush()
            dialog.setOnDismissListener {
                webView.destroy()
                if (!delivered) {
                    delivered = true
                    if (continuation.isActive) {
                        continuation.resume(ConsentPageResult.Failed("Consent was cancelled"))
                    }
                }
            }
            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    if (!delivered) {
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            webView.loadUrl(consentUrl)
        }
    }

    private fun isAuthorizationRedirect(url: String, redirectUri: String): Boolean {
        val actual = Uri.parse(url)
        val expected = Uri.parse(redirectUri)
        return actual.scheme == expected.scheme &&
            actual.host == expected.host &&
            actual.path == expected.path
    }
}
