package com.mandallaz.pikadex.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mandallaz.pikadex.R
import kotlinx.coroutines.CancellationException

class MeteredNetworkException : Exception()

internal const val PREFETCH_NOTIFICATION_CHANNEL_ID = "pikadex_prefetch_channel"
internal const val PREFETCH_NOTIFICATION_ID = 1001

class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val tierNames = inputData.getStringArray("tiers")
        val initialPhaseRes = tierNames?.firstOrNull()?.let {
            try { PrefetchTier.valueOf(it).labelRes } catch (e: Exception) { null }
        } ?: PrefetchTier.ESSENTIALS.labelRes
        return createForegroundInfo(0, 0, initialPhaseRes)
    }

    fun createForegroundInfo(
        done: Int = 0,
        total: Int = 0,
        @StringRes phaseRes: Int = PrefetchTier.ESSENTIALS.labelRes
    ): ForegroundInfo {
        createNotificationChannel()

        val phaseText = applicationContext.getString(phaseRes)
        val titleText = applicationContext.getString(R.string.settings_offline_data_section)
        val cancelText = applicationContext.getString(R.string.settings_cancel)
        val progressText = if (total > 0) "$phaseText — $done/$total" else "$phaseText…"

        val cancelPendingIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, PREFETCH_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .addAction(
                0,
                cancelText,
                cancelPendingIntent
            )
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PREFETCH_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(PREFETCH_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null && notificationManager.getNotificationChannel(PREFETCH_NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    PREFETCH_NOTIFICATION_CHANNEL_ID,
                    applicationContext.getString(R.string.settings_offline_data_section),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private suspend fun updateProgressAndForeground(done: Int, total: Int, phaseRes: Int) {
        setProgress(workDataOf("done" to done, "total" to total, "phaseRes" to phaseRes))
        try {
            setForeground(createForegroundInfo(done, total, phaseRes))
        } catch (e: Exception) {
            // Safe guard if worker is stopping/cancelling
        }
    }

    override suspend fun doWork(): Result {
        val tierNames = inputData.getStringArray("tiers") ?: return Result.failure()
        val tiers = tierNames.mapNotNull {
            try {
                PrefetchTier.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
        if (tiers.isEmpty()) {
            return Result.success()
        }

        val repository = AppContainer.repository
        val appContext = applicationContext

        try {
            try {
                setForeground(createForegroundInfo(0, 0, tiers.first().labelRes))
            } catch (e: Exception) {
                // Ignore if foreground update fails
            }

            var totalFailed = 0
            tiers.forEach { tier ->
                if (abortIfWentMetered(appContext)) {
                    throw MeteredNetworkException()
                }
                updateProgressAndForeground(0, 0, tier.labelRes)
                val units = PrefetchManager.buildUnits(tier, appContext, repository)
                if (abortIfWentMetered(appContext)) {
                    throw MeteredNetworkException()
                }
                updateProgressAndForeground(0, units.size, tier.labelRes)
                totalFailed += runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->
                    if (abortIfWentMetered(appContext)) {
                        throw MeteredNetworkException()
                    }
                    updateProgressAndForeground(done, units.size, tier.labelRes)
                }
            }
            return Result.success(workDataOf("failed" to totalFailed))
        } catch (e: MeteredNetworkException) {
            return Result.failure(workDataOf("messageRes" to R.string.settings_prefetch_error_wifi_required))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(workDataOf("messageRes" to R.string.settings_prefetch_error_network))
        }
    }

    private fun abortIfWentMetered(context: Context): Boolean {
        if (!PrefetchSettings.wifiOnlyEnabled.value) return false
        return PrefetchManager.isActiveNetworkMetered(context)
    }

    companion object {
        private const val PREFETCH_CONCURRENCY = 6
    }
}
