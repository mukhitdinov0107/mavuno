package org.mavuno.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mavuno.app.BuildConfig
import org.mavuno.app.Services
import org.mavuno.app.session.CheckSession
import org.mavuno.app.sync.SyncScheduler
import org.mavuno.fusion.FusionResult
import org.mavuno.fusion.LeafClass
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.math.max

class RecordRepository(private val context: Context, private val services: Services) {

    private val dao get() = services.db().records()

    fun observeAll() = dao.observeAll()

    fun observeQueuedCount() = dao.observeQueuedCount()

    suspend fun byId(id: String) = dao.byId(id)

    /** Stores the record locally whatever the consent; only shared records enter the sync queue. */
    suspend fun save(session: CheckSession, result: FusionResult, shareRecord: Boolean, sharePhotos: Boolean) {
        require(!session.isExample) { "Example checks are never stored" }
        val record = build(session, result, shareRecord, shareRecord && sharePhotos)
        val accept = services.weights.config.photoAcceptProb
        withContext(Dispatchers.IO) {
            dao.insert(
                RecordEntity(
                    recordId = record.recordId,
                    createdAt = session.startedAt,
                    status = record.result.status,
                    topCause = record.result.causes.firstOrNull()?.id,
                    json = FieldRecord.json.encodeToString(FieldRecord.serializer(), record),
                    shareRecord = shareRecord,
                    sharePhotos = record.consent.sharePhotos,
                    syncState = if (shareRecord) SyncState.QUEUED else SyncState.LOCAL_ONLY,
                ),
            )
            dao.insertPhotos(session.photos.map { p ->
                PhotoEntity(p.photoId, session.recordId, p.file?.path.orEmpty(), p.treeIndex, p.leafClass?.id, p.prob, isAccepted(p.leafClass, p.prob, accept))
            })
        }
        session.saved = true
        if (shareRecord) SyncScheduler.enqueue(context)
    }

    /** JSON body for POST /records: the stored record plus resized photos when the farmer allowed it. */
    suspend fun uploadPayload(entity: RecordEntity): String = withContext(Dispatchers.IO) {
        val record = FieldRecord.json.decodeFromString(FieldRecord.serializer(), entity.json)
        if (!entity.sharePhotos) return@withContext entity.json
        val files = dao.photosFor(entity.recordId).associate { it.photoId to File(it.path) }
        val photos = record.photos.map { p -> p.copy(jpegBase64 = files[p.photoId]?.takeIf { it.exists() }?.let(::compressForUpload)) }
        FieldRecord.json.encodeToString(FieldRecord.serializer(), record.copy(photos = photos))
    }

    private fun build(session: CheckSession, result: FusionResult, shareRecord: Boolean, sharePhotos: Boolean): FieldRecord {
        val profile = requireNotNull(services.prefs.profile)
        val plot = requireNotNull(services.prefs.plot)
        val ctx = session.site?.context
        val accept = services.weights.config.photoAcceptProb
        val shown = if (result.causes.isNotEmpty()) result.causes else result.candidates
        return FieldRecord(
            recordId = session.recordId,
            createdAt = Instant.ofEpochMilli(session.startedAt).toString(),
            appVersion = BuildConfig.VERSION_NAME,
            modelVersion = services.classifier.version,
            packVersion = services.regionalPack.version,
            fusionVersion = services.weights.version,
            language = services.prefs.language ?: "sw",
            farmer = FieldRecord.Farmer(profile.farmerId, profile.name, profile.phone, profile.cooperative),
            plot = FieldRecord.PlotInfo(plot.plotId, plot.lat, plot.lon),
            photos = session.photos.map { p ->
                FieldRecord.PhotoInfo(p.photoId, p.treeIndex, p.leafClass?.id, p.prob, isAccepted(p.leafClass, p.prob, accept))
            },
            interview = session.answers.toMap(),
            context = FieldRecord.ContextInfo(
                insidePack = session.site?.insidePack ?: false,
                rainFloweringPct = ctx?.rainFloweringPct,
                rainBerryPct = ctx?.rainBerryPct,
                soilPh = ctx?.soilPh,
                soilN = ctx?.soilNitrogenGPerKg,
                soilK = ctx?.soilPotassiumMgPerKg,
                dataThrough = ctx?.dataThrough,
            ),
            result = FieldRecord.ResultInfo(
                status = result.status.id,
                // For needs_human these are the candidates kept for the officer, never shown to the farmer.
                causes = shown.map { rc ->
                    FieldRecord.CauseInfo(rc.cause.id, round2(rc.prob), rc.label.id, rc.reasons.map { FieldRecord.ReasonInfo(it.id, it.params) })
                },
                abstainReasons = result.abstainReasons.map { it.id },
                knownFeatures = result.knownFeatureCount,
            ),
            consent = FieldRecord.Consent(shareRecord, sharePhotos),
        )
    }

    private fun isAccepted(cls: LeafClass?, prob: Double?, threshold: Double) =
        cls != null && cls != LeafClass.OTHER && (prob ?: 0.0) >= threshold

    private fun round2(x: Double) = Math.round(x * 100) / 100.0

    companion object {
        private const val MAX_UPLOAD_BYTES = 100 * 1024

        /** Downscale and re-encode until the JPEG is at most 100 KB (PRD 7.6). */
        fun compressForUpload(file: File): String? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 800)
            val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            var scaled = bitmap
            var quality = 80
            while (true) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= MAX_UPLOAD_BYTES || (quality <= 40 && scaled.width <= 320)) {
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
                if (quality > 40) quality -= 15
                else scaled = Bitmap.createScaledBitmap(scaled, scaled.width * 3 / 4, scaled.height * 3 / 4, true)
            }
        }
    }
}

