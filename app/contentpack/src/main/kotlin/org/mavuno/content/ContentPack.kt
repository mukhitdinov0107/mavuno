package org.mavuno.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mavuno.fusion.Cause
import org.mavuno.fusion.ReasonRef

@Serializable
data class PackManifest(
    @SerialName("pack_id") val packId: String,
    val language: String,
    @SerialName("language_name") val languageName: String,
    val crop: String,
    val version: String,
)

@Serializable
data class AdviceCard(
    val what: String,
    val check: List<String>,
    @SerialName("first_steps") val firstSteps: List<String>,
    @SerialName("contact_officer") val contactOfficer: String,
)

/**
 * One language pack: a folder with manifest.json, strings.json, cards.json and audio/<id>.opus.
 * Every user-facing string goes through [text] or [card]; a missing ID is an error, never a fallback to free text.
 */
class ContentPack(
    val manifest: PackManifest,
    private val strings: Map<String, String>,
    private val cards: Map<String, AdviceCard>,
) {
    val stringIds: Set<String> get() = strings.keys
    val cardIds: Set<String> get() = cards.keys

    fun text(id: String, params: Map<String, String> = emptyMap()): String {
        val template = strings[id] ?: throw MissingContentException(manifest.language, id)
        return fill(template, params, id)
    }

    fun text(ref: ReasonRef): String = text(ref.id, ref.params)

    fun card(cause: Cause): AdviceCard = cards[cause.id] ?: throw MissingContentException(manifest.language, "card.${cause.id}")

    /** Audio clip path for a string ID, relative to the pack folder. */
    fun audioPath(id: String): String = "audio/$id.opus"

    private fun fill(template: String, params: Map<String, String>, id: String): String =
        PLACEHOLDER.replace(template) { m ->
            params[m.groupValues[1]] ?: throw IllegalArgumentException("${manifest.language}/$id: missing param '${m.groupValues[1]}'")
        }

    companion object {
        val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")
        private val json = Json { ignoreUnknownKeys = true }

        /** [read] returns the text of a file inside the pack folder (assets on Android, files on the JVM). */
        fun load(read: (String) -> String): ContentPack = ContentPack(
            manifest = json.decodeFromString(PackManifest.serializer(), read("manifest.json")),
            strings = json.decodeFromString(read("strings.json")),
            cards = json.decodeFromString(read("cards.json")),
        )
    }
}

class MissingContentException(language: String, id: String) : IllegalStateException("Content ID '$id' missing from '$language' pack")
