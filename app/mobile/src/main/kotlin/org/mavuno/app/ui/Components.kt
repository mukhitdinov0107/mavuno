package org.mavuno.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mavuno.app.services
import org.mavuno.content.ContentPack

val LocalPack = staticCompositionLocalOf<ContentPack> { error("No content pack provided") }
val LocalLanguage = staticCompositionLocalOf { "sw" }

/** Every user-facing string comes from the active content pack by ID (PRD 10: no free text). */
@Composable
fun t(id: String, vararg params: Pair<String, Any>): String =
    LocalPack.current.text(id, params.associate { it.first to it.second.toString() })

/** Standard scrolling screen. */
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

/**
 * The screen's main prompt, read aloud on arrival (PRD 5, step 2). The speaker button appears only when a
 * recorded clip exists for this ID, so there is never a dead control on screen.
 */
@Composable
fun Prompt(id: String, vararg params: Pair<String, Any>, autoPlay: Boolean = true) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t(id, *params), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        SpeakButton(id, autoPlay)
    }
}

@Composable
fun SpeakButton(id: String, autoPlay: Boolean = true) {
    val context = LocalContext.current
    val language = LocalLanguage.current
    val audio = context.services.audio
    val hasClip = remember(language, id) { audio.hasClip(language, id) }
    if (!hasClip) return
    if (autoPlay) {
        LaunchedEffect(language, id) { audio.play(language, id) }
        DisposableEffect(language, id) { onDispose { audio.stop() } }
    }
    IconButton(Pic.Speaker, t("a11y.speak")) { audio.play(language, id) }
}

/** Scales down slightly while pressed, so every tap visibly lands. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
}

enum class ButtonTone { Primary, Quiet, Danger }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Primary,
    pic: Pic? = null,
) {
    val (bg, fg) = when {
        !enabled -> Palette.Line to Palette.InkSoft
        tone == ButtonTone.Primary -> Palette.Leaf to Palette.Paper
        tone == ButtonTone.Danger -> Palette.CherryMist to Palette.Cherry
        else -> Palette.LeafMist to Palette.LeafDeep
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .pressable(enabled, onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (pic != null) Pictogram(pic, Modifier.size(30.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.Leaf,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/** A picture answer. The picture carries the meaning; the word is there for whoever can read it. */
@Composable
fun AnswerTile(pic: Pic, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, picSize: Dp = 72.dp) {
    val bg by animateColorAsState(if (selected) Palette.Leaf else Palette.LeafMist, label = "tileBg")
    val fg by animateColorAsState(if (selected) Palette.Paper else Palette.Ink, label = "tileFg")
    val lift by animateFloatAsState(if (selected) 1.04f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "tileLift")
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .pressable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(picSize + 14.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
            Pictogram(pic, Modifier.size(picSize))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg, textAlign = TextAlign.Center)
    }
}

/** The same picture answer laid out as a full-width row, for answers with longer words. */
@Composable
fun AnswerRow(pic: Pic, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Leaf else Palette.LeafMist, label = "rowBg")
    val fg by animateColorAsState(if (selected) Palette.Paper else Palette.Ink, label = "rowFg")
    val lift by animateFloatAsState(if (selected) 1.03f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "rowLift")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .pressable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
            Pictogram(pic, Modifier.size(68.dp))
        }
        Text(label, style = MaterialTheme.typography.titleLarge, color = fg, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Pill(text: String, pic: Pic? = null, bg: Color = Palette.LeafMist, fg: Color = Palette.Ink, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pic != null) Pictogram(pic, Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

/** A row of evidence: where it came from (picture) and what it says. */
@Composable
fun EvidenceRow(pic: Pic, text: String, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (muted) Palette.Line else Palette.Paper),
            contentAlignment = Alignment.Center,
        ) { Pictogram(pic, Modifier.size(28.dp).graphicsLayer { alpha = if (muted) 0.45f else 1f }) }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (muted) Palette.InkSoft else Palette.Ink, modifier = Modifier.weight(1f))
    }
}

/** Round icon button. */
@Composable
fun IconButton(pic: Pic, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Palette.LeafMist)
            .pressable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Pictogram(pic, Modifier.size(28.dp)) }
}
