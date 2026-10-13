package com.openxpand.sdk.entitlement

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.net.SocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun cellularSocketFactory(context: Context, timeoutMs: Int): SocketFactory {
    val network = suspendCancellableCoroutine { cont ->
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.unregisterNetworkCallback(this)
                if (cont.isActive) {
                    cont.resume(network)
                }
            }

            override fun onUnavailable() {
                connectivity.unregisterNetworkCallback(this)
                if (cont.isActive) {
                    cont.resumeWithException(
                        EntitlementException("Cellular network is not available")
                    )
                }
            }
        }
        cont.invokeOnCancellation {
            try {
                connectivity.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
        }
        connectivity.requestNetwork(request, callback, timeoutMs)
    }
    return network.socketFactory
}
