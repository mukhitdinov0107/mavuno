package org.mavuno.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mavuno.fusion.AbstainReason
import org.mavuno.fusion.Cause
import org.mavuno.fusion.ConfidenceLabel
import org.mavuno.fusion.FeatureCatalog
import org.mavuno.fusion.FusionEngine
import org.mavuno.fusion.FusionInput
import org.mavuno.fusion.FusionWeights
import org.mavuno.fusion.LeafClass
import org.mavuno.fusion.PhotoObservation
import org.mavuno.fusion.SiteContext
import org.mavuno.fusion.TestContent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentPackTest {

    private val contentDir = File(System.getProperty("mavuno.contentDir"))
    private val languages = listOf("en", "sw")
    private val packs = languages.associateWith { lang -> ContentPack.load { File(contentDir, "packs/$lang/$it").readText() } }

    /** Every string ID the fusion engine and interview can ask the UI to show. */
    private val requiredIds: Set<String> = buildSet {
        Cause.entries.forEach { add("cause.${it.id}.name") }
        ConfidenceLabel.entries.forEach { add(it.stringId) }
        AbstainReason.entries.forEach { add(it.stringId) }
        addAll(listOf("abstain.title", "abstain.message", "answer.unknown", "card.awaiting_review", "card.suspected_note"))
        FeatureCatalog.questions.forEach { (q, answers) ->
            add("question.$q")
            answers.forEach { add("answer.$q.$it") }
        }
        FeatureCatalog.all.forEach { f -> f.values.forEach { add("reason.${f.id}.$it") } }
    }

    @Test
    fun `every pack contains every required string`() {
        for ((lang, pack) in packs) {
            val missing = requiredIds - pack.stringIds
            assertTrue(missing.isEmpty(), "$lang is missing: $missing")
        }
    }

    @Test
    fun `languages have identical string and card IDs`() {
        val (en, sw) = packs.getValue("en") to packs.getValue("sw")
        assertEquals(en.stringIds, sw.stringIds, "string IDs differ: ${en.stringIds symDiff sw.stringIds}")
        assertEquals(en.cardIds, sw.cardIds)
    }

    @Test
    fun `placeholders match across languages`() {
        val en = packs.getValue("en")
        val sw = packs.getValue("sw")
        for (id in en.stringIds) {
            assertEquals(placeholders(en, id), placeholders(sw, id), "placeholders differ for $id")
        }
    }

    @Test
    fun `every cause has a card in every pack and metadata`() {
        val meta = Json.parseToJsonElement(File(contentDir, "cards_meta.json").readText()).jsonObject.getValue("cards").jsonObject
        for (cause in Cause.entries) {
            packs.values.forEach { it.card(cause) }
            assertTrue(cause.id in meta, "cards_meta.json missing ${cause.id}")
        }
    }

    /** PRD 10: no free-text generation. Every fusion output for every fixture renders fully from pack IDs. */
    @Test
    fun `every fusion output renders from the packs with no unfilled placeholders`() {
        val engine = FusionEngine(FusionWeights.parse(File(contentDir, "fusion_weights.json").readText()))
        val inputs = scenarioInputs() + FusionInput(List(6) { PhotoObservation(LeafClass.RUST, 0.9) }, emptyMap(), SiteContext())
        for ((lang, pack) in packs) {
            for (input in inputs) {
                val r = engine.evaluate(input)
                val rendered = buildList {
                    (r.causes + r.candidates).forEach { rc ->
                        add(pack.text(rc.nameId))
                        add(pack.text(rc.label.stringId))
                        rc.reasons.forEach { add(pack.text(it)) }
                        pack.card(rc.cause)
                    }
                    r.abstainReasons.forEach { add(pack.text(it.stringId)) }
                    r.evidence.forEach { add(pack.text(it.reason)) }
                }
                rendered.forEach { assertFalse(ContentPack.PLACEHOLDER.containsMatchIn(it), "$lang: unfilled placeholder in '$it'") }
            }
        }
    }

    private fun placeholders(pack: ContentPack, id: String): Set<String> {
        val raw = rawStrings.getValue(pack.manifest.language).getValue(id).jsonPrimitive.content
        return ContentPack.PLACEHOLDER.findAll(raw).map { it.groupValues[1] }.toSet()
    }

    private val rawStrings = languages.associateWith { lang ->
        Json.parseToJsonElement(File(contentDir, "packs/$lang/strings.json").readText()).jsonObject
    }

    private fun scenarioInputs(): List<FusionInput> = TestContent.scenarios.map { it.input }

    private infix fun Set<String>.symDiff(other: Set<String>) = (this - other) + (other - this)
}
