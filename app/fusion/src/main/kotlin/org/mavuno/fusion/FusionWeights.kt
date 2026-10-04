package org.mavuno.fusion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Thresholds for fusion. Defaults mirror the PRD; the values actually used come from fusion_weights.json. */
@Serializable
data class FusionConfig(
    @SerialName("photo_accept_prob") val photoAcceptProb: Double = 0.70,
    @SerialName("seen_min_count") val seenMinCount: Int = 2,
    @SerialName("seen_min_share") val seenMinShare: Double = 0.30,
    @SerialName("healthy_mostly_share") val healthyMostlyShare: Double = 0.70,
    @SerialName("min_accepted_photos") val minAcceptedPhotos: Int = 3,
    @SerialName("min_known_features") val minKnownFeatures: Int = 5,
    @SerialName("abstain_top_prob") val abstainTopProb: Double = 0.40,
    @SerialName("conflict_margin") val conflictMargin: Double = 0.10,
    @SerialName("output_min_prob") val outputMinProb: Double = 0.25,
    @SerialName("max_causes") val maxCauses: Int = 3,
    @SerialName("label_likely") val labelLikely: Double = 0.60,
    @SerialName("label_possible") val labelPossible: Double = 0.40,
    @SerialName("rain_deficit_pct") val rainDeficitPct: Int = 70,
    @SerialName("rain_excess_pct") val rainExcessPct: Int = 140,
    @SerialName("soil_acid_ph") val soilAcidPh: Double = 5.0,
    @SerialName("soil_low_n_g_per_kg") val soilLowNitrogenGPerKg: Double = 1.0,
    @SerialName("soil_low_k_mg_per_kg") val soilLowPotassiumMgPerKg: Double = 100.0,
)

@Serializable
data class CauseWeights(
    val prior: Double = 0.0,
    val weights: Map<String, Double> = emptyMap(),
)

@Serializable
data class FusionWeights(
    val version: String,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    val config: FusionConfig = FusionConfig(),
    val causes: Map<String, CauseWeights>,
) {
    init {
        val expected = Cause.entries.map { it.id }.toSet()
        require(causes.keys == expected) {
            "fusion_weights causes must be exactly $expected; missing=${expected - causes.keys}, extra=${causes.keys - expected}"
        }
        causes.forEach { (cause, cw) ->
            val bad = cw.weights.keys - FeatureCatalog.allKeys
            require(bad.isEmpty()) { "Cause $cause uses unknown feature keys: $bad" }
        }
    }

    fun forCause(cause: Cause): CauseWeights = causes.getValue(cause.id)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): FusionWeights = json.decodeFromString(serializer(), text)
    }
}
