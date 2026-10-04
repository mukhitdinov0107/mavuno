package org.mavuno.fusion

import kotlin.math.abs
import kotlin.math.exp

/**
 * Expert-weighted evidence table (PRD 7.4). Not a learned model:
 * score(cause) = prior + sum of weight(cause, feature=value) over known features, then softmax.
 * Unknown features contribute zero.
 */
class FusionEngine(private val weights: FusionWeights) {

    private val config get() = weights.config

    fun evaluate(input: FusionInput): FusionResult {
        val extracted = FeatureExtractor.extract(input, config)
        val observations = extracted.observations

        val contributions: Map<Cause, List<Pair<Observation, Double>>> = Cause.entries.associateWith { cause ->
            val cw = weights.forCause(cause).weights
            observations.mapNotNull { obs -> cw[obs.key]?.let { obs to it } }
        }
        val scores = Cause.entries.associateWith { cause ->
            weights.forCause(cause).prior + contributions.getValue(cause).sumOf { it.second }
        }
        val probabilities = softmax(scores)
        val ranked = Cause.entries.sortedByDescending { probabilities.getValue(it) }

        val knownFeatureCount = observations.count { it.source != FeatureSource.PHOTO } +
            (if (extracted.photosUsable) 1 else 0)

        val abstain = buildList {
            if (extracted.photoSummary.accepted < config.minAcceptedPhotos) add(AbstainReason.TOO_FEW_PHOTOS)
            if (probabilities.getValue(ranked.first()) < config.abstainTopProb) add(AbstainReason.LOW_TOP_PROBABILITY)
            if (knownFeatureCount < config.minKnownFeatures) add(AbstainReason.TOO_FEW_KNOWN_FEATURES)
            if (hasConflict(contributions, probabilities, ranked)) add(AbstainReason.PHOTO_INTERVIEW_CONFLICT)
        }

        val candidates = ranked
            .filter { probabilities.getValue(it) >= config.outputMinProb }
            .take(config.maxCauses)
            .map { cause -> rank(cause, probabilities.getValue(cause), contributions.getValue(cause)) }

        val status = if (abstain.isEmpty()) ResultStatus.OK else ResultStatus.NEEDS_HUMAN
        return FusionResult(
            status = status,
            causes = if (status == ResultStatus.OK) candidates else emptyList(),
            candidates = candidates,
            abstainReasons = abstain,
            evidence = observations,
            unknownFeatures = extracted.unknownFeatures,
            knownFeatureCount = knownFeatureCount,
            photoSummary = extracted.photoSummary,
            probabilities = probabilities,
        )
    }

    /**
     * Photos and interview each favour a different cause, and those two causes are the
     * top two overall with probabilities closer than the conflict margin.
     */
    private fun hasConflict(
        contributions: Map<Cause, List<Pair<Observation, Double>>>,
        probabilities: Map<Cause, Double>,
        ranked: List<Cause>,
    ): Boolean {
        val photoTop = topBySource(contributions, FeatureSource.PHOTO) ?: return false
        val interviewTop = topBySource(contributions, FeatureSource.INTERVIEW) ?: return false
        if (photoTop == interviewTop) return false
        val topTwo = ranked.take(2).toSet()
        if (photoTop !in topTwo || interviewTop !in topTwo) return false
        return abs(probabilities.getValue(photoTop) - probabilities.getValue(interviewTop)) < config.conflictMargin
    }

    private fun topBySource(contributions: Map<Cause, List<Pair<Observation, Double>>>, source: FeatureSource): Cause? =
        contributions
            .mapValues { (_, list) -> list.filter { it.first.source == source }.sumOf { it.second } }
            .filterValues { it > 0.0 }
            .maxByOrNull { it.value }
            ?.key

    private fun rank(cause: Cause, prob: Double, contribs: List<Pair<Observation, Double>>): RankedCause {
        val label = when {
            prob >= config.labelLikely -> ConfidenceLabel.LIKELY
            prob >= config.labelPossible -> ConfidenceLabel.POSSIBLE
            else -> ConfidenceLabel.WORTH_CHECKING
        }
        val reasons = contribs
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(2)
            .map { it.first.reason }
        return RankedCause(cause, prob, label, reasons)
    }

    private fun softmax(scores: Map<Cause, Double>): Map<Cause, Double> {
        val max = scores.values.max()
        val exps = scores.mapValues { exp(it.value - max) }
        val sum = exps.values.sum()
        return exps.mapValues { it.value / sum }
    }
}
