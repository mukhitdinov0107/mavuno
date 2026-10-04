package org.mavuno.fusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FusionEngineTest {

    private val engine = FusionEngine(TestContent.weights)
    private val sixHealthy = List(6) { PhotoObservation(LeafClass.HEALTHY, 0.9) }

    @Test
    fun `unknown answers contribute zero and are listed as unknown`() {
        val withUnknown = engine.evaluate(FusionInput(sixHealthy, mapOf("q_pruning" to UNKNOWN_ANSWER), SiteContext()))
        val withNothing = engine.evaluate(FusionInput(sixHealthy, emptyMap(), SiteContext()))
        assertEquals(withNothing.probabilities, withUnknown.probabilities)
        assertTrue("q_pruning" in withUnknown.unknownFeatures)
    }

    @Test
    fun `plot outside the pack makes every context feature unknown`() {
        val r = engine.evaluate(FusionInput(sixHealthy, emptyMap(), SiteContext()))
        assertTrue(r.unknownFeatures.containsAll(listOf("rain_flowering", "rain_berry", "soil_ph", "soil_n", "soil_k")))
    }

    @Test
    fun `photos below the acceptance threshold or classed other are not evidence`() {
        val photos = List(3) { PhotoObservation(LeafClass.RUST, 0.69) } + List(3) { PhotoObservation(LeafClass.OTHER, 0.99) }
        val r = engine.evaluate(FusionInput(photos, emptyMap(), SiteContext()))
        assertEquals(0, r.photoSummary.accepted)
        assertTrue(AbstainReason.TOO_FEW_PHOTOS in r.abstainReasons)
    }

    @Test
    fun `a class is seen only on at least 2 photos and 30 percent of accepted`() {
        val summary = { photos: List<PhotoObservation> -> FeatureExtractor.summarisePhotos(photos, TestContent.weights.config) }
        val rust = { n: Int -> List(n) { PhotoObservation(LeafClass.RUST, 0.9) } }
        val healthy = { n: Int -> List(n) { PhotoObservation(LeafClass.HEALTHY, 0.9) } }
        assertFalse(LeafClass.RUST in summary(rust(1) + healthy(1)).seen, "1 photo is never enough")
        assertFalse(LeafClass.RUST in summary(rust(2) + healthy(5)).seen, "2 of 7 is under 30%")
        assertTrue(LeafClass.RUST in summary(rust(2) + healthy(4)).seen, "2 of 6 is over 30%")
    }

    @Test
    fun `abstain hides causes from the farmer but keeps candidates for the officer`() {
        val photos = List(6) { PhotoObservation(LeafClass.RUST, 0.95) }
        val r = engine.evaluate(FusionInput(photos, emptyMap(), SiteContext()))
        assertEquals(ResultStatus.NEEDS_HUMAN, r.status)
        assertTrue(r.causes.isEmpty())
        assertEquals(Cause.LEAF_RUST, r.candidates.first().cause)
    }

    @Test
    fun `berry causes are always marked suspected`() {
        assertTrue(Cause.BERRY_DISEASE_SUSPECTED.isSuspected)
        assertTrue(Cause.BERRY_BORER_SUSPECTED.isSuspected)
        assertFalse(Cause.LEAF_RUST.isSuspected)
    }

    @Test
    fun `weights with an unknown feature key are rejected`() {
        val bad = TestContent.weights.causes.toMutableMap()
        bad["leaf_rust"] = CauseWeights(weights = mapOf("photo.rust=everywhere" to 1.0))
        assertFailsWith<IllegalArgumentException> { TestContent.weights.copy(causes = bad) }
    }

    @Test
    fun `weights must list exactly the fixed causes`() {
        val missing = TestContent.weights.causes - "phoma"
        assertFailsWith<IllegalArgumentException> { TestContent.weights.copy(causes = missing) }
    }

    @Test
    fun `an answer outside the enumerated list is a programming error`() {
        assertFailsWith<IllegalArgumentException> {
            engine.evaluate(FusionInput(sixHealthy, mapOf("q_shade" to "lots"), SiteContext()))
        }
    }
}
