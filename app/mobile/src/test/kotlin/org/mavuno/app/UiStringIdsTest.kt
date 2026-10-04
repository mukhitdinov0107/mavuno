package org.mavuno.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every literal string ID the UI asks for (t("…"), SpokenPrompt("…"), Notice(t("…"))…) must exist in every pack.
 * Dynamic IDs ("cause.$id.name" etc.) are covered by the content-pack tests.
 */
class UiStringIdsTest {
    private val content = File("../../content")
    private val sources = File("src/main/kotlin")

    @Test
    fun `every literal UI string ID exists in every language pack`() {
        val pattern = Regex("""(?:\bt|SpokenPrompt)\(\s*"([a-z_]+(?:\.[a-z0-9_]+)+)"""")
        val ifPattern = Regex("""if \([^)]*\) "([a-z_]+\.[a-z0-9_.]+)" else "([a-z_]+\.[a-z0-9_.]+)"""")
        val ids = sources.walk().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            pattern.findAll(text).map { it.groupValues[1] } + ifPattern.findAll(text).flatMap { listOf(it.groupValues[1], it.groupValues[2]) }
        }.toSet()
        assertTrue(ids.size > 40, "scanner found only ${ids.size} IDs; pattern probably broke")

        for (lang in listOf("en", "sw")) {
            val keys = Json.parseToJsonElement(File(content, "packs/$lang/strings.json").readText()).jsonObject.keys
            val missing = ids - keys
            assertTrue(missing.isEmpty(), "$lang pack is missing UI IDs: $missing")
        }
    }
}
