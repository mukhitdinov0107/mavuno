package org.mavuno.app.audio

import android.content.Context
import android.media.MediaPlayer

/**
 * Plays the pre-rendered clip for a string ID from content/packs/<lang>/audio/<id>.opus.
 * A missing clip is silent; the text is always on screen.
 */
class PromptPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun hasClip(language: String, id: String): Boolean =
        runCatching { context.assets.openFd(path(language, id)).close(); true }.getOrDefault(false)

    fun play(language: String, id: String) {
        stop()
        val fd = runCatching { context.assets.openFd(path(language, id)) }.getOrNull() ?: return
        player = MediaPlayer().apply {
            fd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            setOnCompletionListener { stop() }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }

    private fun path(language: String, id: String) = "content/packs/$language/audio/$id.opus"
}
