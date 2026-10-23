package com.openxpand.sdk.internal

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object SecurityProviderUpdater {

    private const val RETRY_AFTER_FAILURE_MS = 60_000L
    private val installLock = Any()

    @Volatile
    private var installStatus: InstallStatus = InstallStatus.NotAttempted
    @Volatile
    private var lastAttemptElapsedMs: Long = 0L

    suspend fun ensureUpToDate(context: Context): InstallStatus {
        val now = SystemClock.elapsedRealtime()
        val current = installStatus
        if (shouldUseCachedStatus(current, now)) return current

        return withContext(Dispatchers.IO) {
            synchronized(installLock) {
                val lockedNow = SystemClock.elapsedRealtime()
                val latest = installStatus
                if (shouldUseCachedStatus(latest, lockedNow)) return@synchronized latest

                val result = try {
                    ProviderInstaller.installIfNeeded(context.applicationContext)
                    InstallStatus.Installed
                } catch (e: GooglePlayServicesRepairableException) {
                    InstallStatus.RepairableFailure(e.connectionStatusCode, e)
                } catch (e: GooglePlayServicesNotAvailableException) {
                    InstallStatus.NotAvailableFailure(e.errorCode, e)
                }

                installStatus = result
                lastAttemptElapsedMs = lockedNow
                result
            }
        }
    }

    private fun shouldUseCachedStatus(status: InstallStatus, nowElapsedMs: Long): Boolean {
        return when (status) {
            is InstallStatus.Installed -> true
            is InstallStatus.NotAttempted -> false
            is InstallStatus.RepairableFailure, is InstallStatus.NotAvailableFailure ->
                (nowElapsedMs - lastAttemptElapsedMs) < RETRY_AFTER_FAILURE_MS
        }
    }

    sealed class InstallStatus {
        data object NotAttempted : InstallStatus()
        data object Installed : InstallStatus()
        data class RepairableFailure(
            val connectionStatusCode: Int,
            val cause: GooglePlayServicesRepairableException
        ) : InstallStatus()
        data class NotAvailableFailure(
            val errorCode: Int,
            val cause: GooglePlayServicesNotAvailableException
        ) : InstallStatus()
    }
}
