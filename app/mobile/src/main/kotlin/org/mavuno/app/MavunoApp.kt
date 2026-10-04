package org.mavuno.app

import android.app.Application
import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mavuno.app.audio.PromptPlayer
import org.mavuno.app.data.MavunoDb
import org.mavuno.app.data.RecordRepository
import org.mavuno.app.datapack.RegionalPack
import org.mavuno.app.datapack.SiteLookup
import org.mavuno.app.ml.LeafClassifier
import org.mavuno.app.session.CapturedPhoto
import org.mavuno.app.session.CheckSession
import org.mavuno.app.sync.SyncScheduler
import org.mavuno.content.ContentPack
import org.mavuno.fusion.Cause
import org.mavuno.fusion.FusionEngine
import org.mavuno.fusion.FusionWeights
import org.mavuno.fusion.LeafClass
import org.mavuno.fusion.SiteContext

class MavunoApp : Application() {
    lateinit var services: Services
        private set

    override fun onCreate() {
        super.onCreate()
        services = Services(this)
        SyncScheduler.enqueue(this)
    }
}

val Context.services: Services get() = (applicationContext as MavunoApp).services

/** Hand-wired dependencies. Everything here works with no network. */
class Services(private val app: Context) {
    val prefs = Prefs(app)

    val weights: FusionWeights = FusionWeights.parse(asset("content/fusion_weights.json"))
    val fusion = FusionEngine(weights)

    /** Causes whose advice card has been reviewed by an agronomist. */
    val reviewedCards: Set<Cause> = run {
        val cards = Json.parseToJsonElement(asset("content/cards_meta.json")).jsonObject.getValue("cards").jsonObject
        Cause.entries.filter { cards[it.id]?.jsonObject?.get("reviewed_by")?.jsonPrimitive?.content.let { v -> v != null && v != "null" } }.toSet()
    }

    val availableLanguages: List<String> = app.assets.list("content/packs")?.sorted().orEmpty()

    /** Swahili first: it is the main language of the people this app is for. */
    val languagesInOrder: List<String> = availableLanguages.sortedBy { if (it == "sw") 0 else 1 }

    private val packs = mutableMapOf<String, ContentPack>()

    fun pack(language: String): ContentPack = synchronized(packs) {
        packs.getOrPut(language) { ContentPack.load { asset("content/packs/$language/$it") } }
    }

    val classifier: LeafClassifier by lazy { LeafClassifier.load(app) }
    val regionalPack: RegionalPack by lazy { RegionalPack.load(app) }
    val audio = PromptPlayer(app)

    @Volatile private var db: MavunoDb? = null

    fun db(): MavunoDb = db ?: synchronized(this) { db ?: MavunoDb.open(app).also { db = it } }

    val records = RecordRepository(app, this)

    /** The check currently in progress. Replaced on every "check the farm". */
    var session = CheckSession()
        private set

    fun newSession(): CheckSession = CheckSession().also { session = it }

    /**
     * A labelled example so anyone can see a full result before the leaf model exists:
     * rust on 4 of 6 leaves, unfed and overdue for pruning, dry flowering season (PRD 13, step 5).
     */
    fun newExampleSession(): CheckSession = CheckSession(isExample = true).apply {
        repeat(4) { photos += CapturedPhoto("example-rust-$it", null, it % 3 + 1, LeafClass.RUST, 0.91) }
        repeat(2) { photos += CapturedPhoto("example-healthy-$it", null, 3, LeafClass.HEALTHY, 0.88) }
        treeIndex = 3
        answers.putAll(
            mapOf(
                "q_tree_age" to "15_30", "q_pruning" to "longer", "q_fertilizer" to "no", "q_shade" to "some",
                "q_berry_spots" to "none", "q_berry_holes" to "none", "q_yield_change" to "somewhat_less", "q_when_noticed" to "berry_growth",
            ),
        )
        site = SiteLookup(true, SiteContext(rainFloweringPct = 62, rainBerryPct = 105, soilPh = 5.4, soilNitrogenGPerKg = 1.2, soilPotassiumMgPerKg = 150.0, dataThrough = "2026-08"))
    }.also { session = it }

    /** "Delete all my data": records, photos, sync queue, profile, plot and the database key. */
    fun deleteEverything() {
        SyncScheduler.cancel(app)
        synchronized(this) {
            db?.close()
            db = null
            MavunoDb.deleteFiles(app)
        }
        app.filesDir.resolve("photos").deleteRecursively()
        prefs.clear()
        session = CheckSession()
    }

    private fun asset(path: String): String = app.assets.open(path).bufferedReader().use { it.readText() }
}
