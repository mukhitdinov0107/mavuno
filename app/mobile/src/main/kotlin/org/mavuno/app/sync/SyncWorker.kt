package org.mavuno.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mavuno.app.BuildConfig
import org.mavuno.app.services
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Sends queued records and pulls officer replies. WorkManager persists the job across app kills and reboots
 * and only runs it with a network. POST /records is idempotent on record_id, so a retry after a lost
 * response cannot create a duplicate.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val services = applicationContext.services
        val profile = services.prefs.profile ?: return@withContext Result.success()
        val dao = services.db().records()

        var failed = false
        for (record in dao.queued()) {
            val code = runCatching { request("POST", "/records", services.records.uploadPayload(record)).first }.getOrDefault(-1)
            if (code in 200..299 || code == 409) dao.markSent(record.recordId) else {
                dao.bumpAttempts(record.recordId)
                failed = true
            }
        }

        if (dao.sentAwaitingReply().isNotEmpty()) {
            runCatching {
                val (code, body) = request("GET", "/farmers/${URLEncoder.encode(profile.farmerId, "UTF-8")}/replies", null)
                if (code == 200) {
                    Json.parseToJsonElement(body).jsonArray.forEach { el ->
                        val o = el.jsonObject
                        dao.setReply(o.getValue("record_id").jsonPrimitive.content, o.getValue("reply").jsonPrimitive.content)
                    }
                }
            }
        }

        if (failed) Result.retry() else Result.success()
    }

    private fun request(method: String, path: String, body: String?): Pair<Int, String> {
        val conn = URL(BuildConfig.BACKEND_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $COOP_TOKEN")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Demo cooperative token; the real one is issued per cooperative at set-up. */
        const val COOP_TOKEN = "demo-coop-token"
    }
}

object SyncScheduler {
    private const val NOW = "sync-now"
    private const val PERIODIC = "sync-periodic"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueue(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(NOW)
            cancelUniqueWork(PERIODIC)
        }
    }
}
