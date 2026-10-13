package com.openxpand.sdk

/**
 * Configuration for a GSMA TS.43 entitlement client (ap2014 AcquireTemporaryToken).
 *
 * The entitlement host is operator-specific. The lab server is plain HTTP, and this
 * library's network security config only allows cleartext to `*.openxpand.com`.
 * The host app must allow cleartext to [entitlementServerUrl] when that URL is `http`.
 *
 * [mcc] and [mnc] are the home network digits as they appear in the IMSI (MNC is not
 * zero-padded). The EAP identity uses a 3-digit MNC in the domain, so MNC `34`
 * becomes `mnc034`.
 *
 * @param entitlementServerUrl Base URL of the entitlement server, without a query string.
 * @param forceCellular Bind the HTTP exchange to a cellular network. Turn this off only
 * when the entitlement server is reached over Wi-Fi or an emulator route.
 * @param subscriptionId SIM subscription to authenticate. `null` uses the default voice subscription.
 * @param terminalId Optional IMEI sent as `terminal_id`. The lab server does not validate it.
 */
data class EntitlementConfig(
    val entitlementServerUrl: String,
    val mcc: String = "722",
    val mnc: String = "34",
    val entitlementVersion: String = "12.0",
    val terminalVendor: String = "Google",
    val terminalModel: String = "Android",
    val terminalSoftwareVersion: String = "14",
    val terminalId: String = "0",
    val forceCellular: Boolean = true,
    val cellularNetworkTimeoutMs: Int = 20_000,
    val subscriptionId: Int? = null
)
