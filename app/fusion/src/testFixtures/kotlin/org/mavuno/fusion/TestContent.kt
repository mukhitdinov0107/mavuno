package org.mavuno.fusion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

object TestContent {
    val dir: File = File(System.getProperty("mavuno.contentDir") ?: error("mavuno.contentDir not set"))

    val weights: FusionWeights by lazy { FusionWeights.parse(File(dir, "fusion_weights.json").readText()) }

    data class Scenario(
        val id: String,
        val input: FusionInput,
        val status: String,
        val top: String?,
        val includes: List<String>,
        val excludes: List<String>,
        val abstainReasons: List<String>,
    )

    val scenarios: List<Scenario> by lazy {
        val root = Json.parseToJsonElement(File(dir, "fixtures/fusion_scenarios.json").readText()).jsonObject
        root.getValue("scenarios").jsonArray.map { parseScenario(it.jsonObject) }
    }

    private fun parseScenario(o: JsonObject): Scenario {
        val photos = o.getValue("photos").jsonArray.flatMap { row ->
            val (cls, prob, count) = (row as JsonArray).map { it.jsonPrimitive }
            List(count.int) { PhotoObservation(LeafClass.fromId(cls.content), prob.double) }
        }
        val interview = o.getValue("interview").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val ctx = o.getValue("context").jsonObject
        fun num(key: String) = ctx[key]?.jsonPrimitive?.doubleOrNull
        val context = SiteContext(
            rainFloweringPct = ctx["rain_flowering_pct"]?.jsonPrimitive?.intOrNull,
            rainBerryPct = ctx["rain_berry_pct"]?.jsonPrimitive?.intOrNull,
            soilPh = num("soil_ph"),
            soilNitrogenGPerKg = num("soil_n"),
            soilPotassiumMgPerKg = num("soil_k"),
            dataThrough = ctx["data_through"]?.jsonPrimitive?.content,
        )
        val expect = o.getValue("expect").jsonObject
        fun list(key: String) = expect[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        return Scenario(
            id = o.getValue("id").jsonPrimitive.content,
            input = FusionInput(photos, interview, context),
            status = expect.getValue("status").jsonPrimitive.content,
            top = expect["top"]?.jsonPrimitive?.content,
            includes = list("includes"),
            excludes = list("excludes"),
            abstainReasons = list("abstain_reasons"),
        )
    }
}
