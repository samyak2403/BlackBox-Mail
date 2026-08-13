package com.samyak.blackboxmailt.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samyak.blackboxmailt.data.MailRepository
import com.samyak.blackboxmailt.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Periodic background check for new mail.
 *
 * The in-app poll only runs while the app is alive, so this worker covers the closed-app case.
 * WorkManager's minimum period is 15 minutes, which is the floor Android enforces - it is not a
 * substitute for the fast in-app polling.
 */
class NewMailWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsStore.getInstance(context).current
        if (!settings.newMailNotifications || !settings.backgroundChecks) return Result.success()
        if (!MailNotifier.hasPermission(context)) return Result.success()

        val repository = MailRepository(context)
        val store = repository.store

        // Only check a mailbox that already exists; never provision one from the background.
        val stored = store.activeAccount() ?: return Result.success()

        return try {
            val session = repository.session()
            val address = session.account.address
            if (address != stored.address && store.activeAccount()?.address != address) {
                return Result.success()
            }

            val messages = repository.messages()
            val known = store.notifiedIds(address)
            val hasBaseline = store.hasNotificationBaseline(address)
            val allIds = messages.map { it.id }.toSet()

            if (!hasBaseline) {
                // First look at this mailbox: record what is there instead of announcing it.
                store.setNotifiedIds(address, allIds)
                return Result.success()
            }

            val fresh = messages.filter { it.id !in known && !it.seen }
            if (fresh.isNotEmpty()) {
                MailNotifier.notifyNewMail(context, address, fresh)
            }
            store.setNotifiedIds(address, known + allIds)
            Result.success()
        } catch (_: Exception) {
            // Transient network or auth trouble; WorkManager will run us again.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "blackbox-new-mail-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewMailWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Keeps the scheduled work in step with the current settings. */
        fun sync(context: Context) {
            val settings = SettingsStore.getInstance(context).current
            if (settings.newMailNotifications && settings.backgroundChecks) {
                schedule(context)
            } else {
                cancel(context)
            }
        }
    }
}
