package org.mavuno.fusion

/** Output classes of the on-device leaf classifier (PRD 7.1). */
enum class LeafClass(val id: String) {
    HEALTHY("healthy"),
    RUST("rust"),
    LEAF_MINER("leaf_miner"),
    PHOMA("phoma"),
    CERCOSPORA("cercospora"),
    OTHER("other");

    companion object {
        fun fromId(id: String): LeafClass =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown leaf class: $id")
    }
}

/** The fixed list of causes (PRD 7.4). Nothing outside this list can ever be shown. */
enum class Cause(val id: String) {
    LEAF_RUST("leaf_rust"),
    CERCOSPORA_NUTRIENT_STRESS("cercospora_nutrient_stress"),
    LEAF_MINER("leaf_miner"),
    PHOMA("phoma"),
    BERRY_DISEASE_SUSPECTED("berry_disease_suspected"),
    BERRY_BORER_SUSPECTED("berry_borer_suspected"),
    DROUGHT_STRESS("drought_stress"),
    EXCESS_RAIN("excess_rain"),
    SOIL_ACIDITY("soil_acidity"),
    NUTRIENT_DEFICIENCY("nutrient_deficiency"),
    OLD_UNPRUNED_TREES("old_unpruned_trees");

    /** Berry causes have no image model behind them, so they are always "suspected" (PRD 7.4). */
    val isSuspected: Boolean get() = this == BERRY_DISEASE_SUSPECTED || this == BERRY_BORER_SUSPECTED

    companion object {
        fun fromId(id: String): Cause =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown cause: $id")
    }
}

/** One classified photo, after temperature scaling. */
data class PhotoObservation(val leafClass: LeafClass, val prob: Double)

/** Values looked up from the regional data pack. Null means unknown (outside pack, stale, missing). */
data class SiteContext(
    val rainFloweringPct: Int? = null,
    val rainBerryPct: Int? = null,
    val soilPh: Double? = null,
    val soilNitrogenGPerKg: Double? = null,
    val soilPotassiumMgPerKg: Double? = null,
    val dataThrough: String? = null,
)

/**
 * Everything fusion needs for one session.
 * [interview] maps question ID to answer ID; missing or "unknown" answers are treated as unknown.
 */
data class FusionInput(
    val photos: List<PhotoObservation>,
    val interview: Map<String, String>,
    val context: SiteContext,
)

enum class ResultStatus(val id: String) { OK("ok"), NEEDS_HUMAN("needs_human") }

enum class AbstainReason(val id: String) {
    TOO_FEW_PHOTOS("too_few_photos"),
    LOW_TOP_PROBABILITY("low_top_probability"),
    TOO_FEW_KNOWN_FEATURES("too_few_known_features"),
    PHOTO_INTERVIEW_CONFLICT("photo_interview_conflict");

    /** Content-pack string ID shown on the abstain screen. */
    val stringId: String get() = "abstain.$id"
}

enum class ConfidenceLabel(val id: String) {
    LIKELY("likely"),
    POSSIBLE("possible"),
    WORTH_CHECKING("worth_checking");

    val stringId: String get() = "confidence.$id"
}

/** A reason template ID plus the values to fill into it. Never free text. */
data class ReasonRef(val id: String, val params: Map<String, String> = emptyMap())

data class RankedCause(
    val cause: Cause,
    val prob: Double,
    val label: ConfidenceLabel,
    /** Up to two features that pushed this cause up the most. */
    val reasons: List<ReasonRef>,
) {
    val nameId: String get() = "cause.${cause.id}.name"
    val cardId: String get() = "card.${cause.id}"
}

data class PhotoSummary(
    val total: Int,
    val accepted: Int,
    val countsByClass: Map<LeafClass, Int>,
    val seen: Set<LeafClass>,
)

data class FusionResult(
    val status: ResultStatus,
    /** Causes shown to the farmer. Empty when [status] is NEEDS_HUMAN. */
    val causes: List<RankedCause>,
    /** Best guesses kept for the extension officer even when the app abstains. */
    val candidates: List<RankedCause>,
    val abstainReasons: List<AbstainReason>,
    /** What the app could see (rendered as reasons on the abstain screen). */
    val evidence: List<Observation>,
    /** Feature IDs the app could not establish. */
    val unknownFeatures: List<String>,
    val knownFeatureCount: Int,
    val photoSummary: PhotoSummary,
    val probabilities: Map<Cause, Double>,
)
