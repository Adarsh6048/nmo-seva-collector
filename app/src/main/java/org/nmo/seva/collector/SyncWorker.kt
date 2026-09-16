package org.nmo.seva.collector

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.isConfigured()) return Result.failure()
        val db = PaymentDbHelper(applicationContext)
        val items = db.getUnsynced(100)
        if (items.isEmpty()) return Result.success()
        return try {
            if (ApiClient.upload(prefs.backendUrl, prefs.collectorCode, prefs.collectorToken, items)) {
                db.markSynced(items.map { it.eventId })
                if (db.getUnsynced(1).isNotEmpty()) SyncScheduler.enqueue(applicationContext)
                Result.success()
            } else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
