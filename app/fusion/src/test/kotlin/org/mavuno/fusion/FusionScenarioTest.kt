package org.mavuno.fusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs every hand-written scenario in content/fixtures/fusion_scenarios.json. */
class FusionScenarioTest {

    private val engine = FusionEngine(TestContent.weights)

    @Test
    fun `there are at least 20 scenarios`() {
        assertTrue(TestContent.scenarios.size >= 20, "PRD requires 20 fixtures, found ${TestContent.scenarios.size}")
    }

    @Test
    fun `every scenario produces its expected outcome`() {
        val failures = TestContent.scenarios.mapNotNull { s ->
            val r = engine.evaluate(s.input)
            val shown = r.causes.map { it.cause.id }
            val problems = buildList {
                if (r.status.id != s.status) add("status=${r.status.id}, expected ${s.status} (abstain=${r.abstainReasons.map { it.id }})")
                if (s.top != null && shown.firstOrNull() != s.top) add("top=${shown.firstOrNull()}, expected ${s.top}")
                s.includes.filter { it !in shown }.forEach { add("missing $it") }
                s.excludes.filter { it in shown }.forEach { add("should not show $it") }
                val gotReasons = r.abstainReasons.map { it.id }
                s.abstainReasons.filter { it !in gotReasons }.forEach { add("missing abstain reason $it (got $gotReasons)") }
            }
            if (problems.isEmpty()) null else "${s.id}: ${problems.joinToString("; ")}\n    probs=${describe(r)}"
        }
        if (failures.isNotEmpty()) fail("${failures.size} scenario(s) failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `every shown cause has two reasons from features that actually raised its score`() {
        for (s in TestContent.scenarios) {
            val r = engine.evaluate(s.input)
            for (rc in r.causes) {
                assertEquals(2, rc.reasons.size, "${s.id}/${rc.cause.id} should show two reasons, got ${rc.reasons}")
                val cw = TestContent.weights.forCause(rc.cause).weights
                for (reason in rc.reasons) {
                    val obs = r.evidence.single { it.reason.id == reason.id }
                    assertTrue((cw[obs.key] ?: 0.0) > 0.0, "${s.id}/${rc.cause.id}: reason ${reason.id} did not contribute")
                }
            }
        }
    }

    private fun describe(r: FusionResult) =
        r.probabilities.entries.sortedByDescending { it.value }.take(4).joinToString { "${it.key.id}=%.2f".format(it.value) }
}
