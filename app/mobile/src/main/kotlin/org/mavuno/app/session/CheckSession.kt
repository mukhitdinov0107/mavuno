package org.mavuno.app.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mavuno.app.Prefs
import org.mavuno.app.datapack.SiteLookup
import org.mavuno.fusion.FusionInput
import org.mavuno.fusion.FusionResult
import org.mavuno.fusion.LeafClass
import org.mavuno.fusion.PhotoObservation
import java.io.File

/** A photo that passed the quality gate. [leafClass] is null when no classifier is installed. */
data class CapturedPhoto(
    val photoId: String,
    val file: File,
    val treeIndex: Int,
    val leafClass: LeafClass?,
    val prob: Double?,
)

/** State of one "check the farm" walk, from photos to the consent screen. */
class CheckSession {
    val recordId: String = Prefs.newId()
    val startedAt: Long = System.currentTimeMillis()

    val photos = mutableStateListOf<CapturedPhoto>()
    var treeIndex by mutableIntStateOf(1)
    val answers = mutableStateMapOf<String, String>()

    var site: SiteLookup? by mutableStateOf(null)
    var result: FusionResult? by mutableStateOf(null)
    var saved by mutableStateOf(false)

    val treesPhotographed: Int get() = photos.map { it.treeIndex }.distinct().size

    fun fusionInput(): FusionInput = FusionInput(
        photos = photos.mapNotNull { p -> p.leafClass?.let { PhotoObservation(it, p.prob ?: 0.0) } },
        interview = answers.toMap(),
        context = site?.context ?: org.mavuno.fusion.SiteContext(),
    )

    fun photoDir(root: File): File = root.resolve("photos/$recordId").apply { mkdirs() }

    companion object {
        const val MIN_LEAVES = 5
        const val MIN_TREES = 3
    }
}
