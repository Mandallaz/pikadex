package com.mandallaz.pikadex.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mandallaz.pikadex.R
import kotlinx.coroutines.CancellationException

class MeteredNetworkException : Exception()

class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

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
            var totalFailed = 0
            tiers.forEach { tier ->
                if (abortIfWentMetered(appContext)) {
                    throw MeteredNetworkException()
                }
                setProgress(workDataOf("done" to 0, "total" to 0, "phaseRes" to tier.labelRes))
                val units = PrefetchManager.buildUnits(tier, appContext, repository)
                if (abortIfWentMetered(appContext)) {
                    throw MeteredNetworkException()
                }
                setProgress(workDataOf("done" to 0, "total" to units.size, "phaseRes" to tier.labelRes))
                totalFailed += runPrefetchBatch(units, PREFETCH_CONCURRENCY) { done ->
                    if (abortIfWentMetered(appContext)) {
                        throw MeteredNetworkException()
                    }
                    setProgress(workDataOf("done" to done, "total" to units.size, "phaseRes" to tier.labelRes))
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
