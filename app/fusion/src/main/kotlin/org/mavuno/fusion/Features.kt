package org.mavuno.fusion

enum class FeatureSource { PHOTO, INTERVIEW, CONTEXT }

/** A discrete feature and the values it can take. Weights are keyed "feature=value". */
data class FeatureDef(val id: String, val source: FeatureSource, val values: List<String>)

/** A known feature value for this session, with the numbers its reason template needs. */
data class Observation(
    val feature: String,
    val value: String,
    val source: FeatureSource,
    val params: Map<String, String> = emptyMap(),
) {
    val key: String get() = "$feature=$value"
    val reason: ReasonRef get() = ReasonRef("reason.$feature.$value", params)
}

const val UNKNOWN_ANSWER = "unknown"

object FeatureCatalog {
    /** Photo classes that have their own seen / not_seen feature. */
    val diseaseClasses = listOf(LeafClass.RUST, LeafClass.LEAF_MINER, LeafClass.PHOMA, LeafClass.CERCOSPORA)

    /** Interview questions and their answer IDs (PRD 7.2). Every question also accepts "unknown". */
    val questions: Map<String, List<String>> = linkedMapOf(
        "q_tree_age" to listOf("lt5", "5_15", "15_30", "gt30"),
        "q_pruning" to listOf("this_year", "last_year", "longer", "never"),
        "q_fertilizer" to listOf("yes", "little", "no"),
        "q_shade" to listOf("none", "some", "heavy"),
        "q_berry_spots" to listOf("many", "few", "none"),
        "q_berry_holes" to listOf("many", "few", "none"),
        "q_yield_change" to listOf("half_or_less", "somewhat_less", "same"),
        "q_when_noticed" to listOf("flowering", "berry_growth", "harvest"),
    )

    val all: List<FeatureDef> = buildList {
        diseaseClasses.forEach { add(FeatureDef("photo.${it.id}", FeatureSource.PHOTO, listOf("seen", "not_seen"))) }
        add(FeatureDef("photo.healthy", FeatureSource.PHOTO, listOf("mostly", "not_mostly")))
        questions.forEach { (q, answers) -> add(FeatureDef(q, FeatureSource.INTERVIEW, answers)) }
        add(FeatureDef("rain_flowering", FeatureSource.CONTEXT, listOf("deficit", "normal", "excess")))
        add(FeatureDef("rain_berry", FeatureSource.CONTEXT, listOf("deficit", "normal", "excess")))
        add(FeatureDef("soil_ph", FeatureSource.CONTEXT, listOf("acid", "ok")))
        add(FeatureDef("soil_n", FeatureSource.CONTEXT, listOf("low", "ok")))
        add(FeatureDef("soil_k", FeatureSource.CONTEXT, listOf("low", "ok")))
    }

    val byId: Map<String, FeatureDef> = all.associateBy { it.id }

    /** Every "feature=value" key a weight may use. */
    val allKeys: Set<String> = all.flatMap { f -> f.values.map { "${f.id}=$it" } }.toSet()
}

/** Turns raw session input into discrete observations, applying the thresholds in [config]. */
internal object FeatureExtractor {

    data class Extracted(
        val photoSummary: PhotoSummary,
        val observations: List<Observation>,
        val unknownFeatures: List<String>,
        val photosUsable: Boolean,
    )

    fun extract(input: FusionInput, config: FusionConfig): Extracted {
        val summary = summarisePhotos(input.photos, config)
        val photosUsable = summary.accepted >= config.minAcceptedPhotos
        val observations = mutableListOf<Observation>()
        val unknown = mutableListOf<String>()

        val photoFeatures = FeatureCatalog.all.filter { it.source == FeatureSource.PHOTO }
        if (photosUsable) {
            for (cls in FeatureCatalog.diseaseClasses) {
                val count = summary.countsByClass[cls] ?: 0
                val value = if (cls in summary.seen) "seen" else "not_seen"
                observations += Observation("photo.${cls.id}", value, FeatureSource.PHOTO, photoParams(count, summary.accepted))
            }
            val healthy = summary.countsByClass[LeafClass.HEALTHY] ?: 0
            val mostly = healthy.toDouble() / summary.accepted >= config.healthyMostlyShare
            observations += Observation(
                "photo.healthy", if (mostly) "mostly" else "not_mostly", FeatureSource.PHOTO,
                photoParams(healthy, summary.accepted),
            )
        } else {
            photoFeatures.forEach { unknown += it.id }
        }

        for ((question, answers) in FeatureCatalog.questions) {
            val answer = input.interview[question]
            when {
                answer == null || answer == UNKNOWN_ANSWER -> unknown += question
                answer in answers -> observations += Observation(question, answer, FeatureSource.INTERVIEW)
                else -> throw IllegalArgumentException("Unknown answer '$answer' for $question")
            }
        }

        val ctx = input.context
        val through = ctx.dataThrough?.let { mapOf("data_through" to it) }.orEmpty()
        rainObservation("rain_flowering", ctx.rainFloweringPct, through, config)?.let { observations += it } ?: run { unknown += "rain_flowering" }
        rainObservation("rain_berry", ctx.rainBerryPct, through, config)?.let { observations += it } ?: run { unknown += "rain_berry" }
        ctx.soilPh?.let {
            observations += Observation("soil_ph", if (it < config.soilAcidPh) "acid" else "ok", FeatureSource.CONTEXT, mapOf("ph" to "%.1f".format(it)))
        } ?: run { unknown += "soil_ph" }
        ctx.soilNitrogenGPerKg?.let {
            observations += Observation("soil_n", if (it < config.soilLowNitrogenGPerKg) "low" else "ok", FeatureSource.CONTEXT, mapOf("value" to "%.1f".format(it)))
        } ?: run { unknown += "soil_n" }
        ctx.soilPotassiumMgPerKg?.let {
            observations += Observation("soil_k", if (it < config.soilLowPotassiumMgPerKg) "low" else "ok", FeatureSource.CONTEXT, mapOf("value" to "%.0f".format(it)))
        } ?: run { unknown += "soil_k" }

        return Extracted(summary, observations, unknown, photosUsable)
    }

    fun summarisePhotos(photos: List<PhotoObservation>, config: FusionConfig): PhotoSummary {
        val accepted = photos.filter { it.leafClass != LeafClass.OTHER && it.prob >= config.photoAcceptProb }
        val counts = accepted.groupingBy { it.leafClass }.eachCount()
        val seen = counts.filter { (_, n) ->
            n >= config.seenMinCount && n.toDouble() / accepted.size >= config.seenMinShare
        }.keys
        return PhotoSummary(photos.size, accepted.size, counts, seen)
    }

    private fun photoParams(count: Int, accepted: Int) = mapOf("count" to count.toString(), "accepted" to accepted.toString())

    private fun rainObservation(feature: String, pct: Int?, through: Map<String, String>, config: FusionConfig): Observation? {
        pct ?: return null
        val value = when {
            pct < config.rainDeficitPct -> "deficit"
            pct > config.rainExcessPct -> "excess"
            else -> "normal"
        }
        return Observation(feature, value, FeatureSource.CONTEXT, mapOf("pct" to pct.toString()) + through)
    }
}
