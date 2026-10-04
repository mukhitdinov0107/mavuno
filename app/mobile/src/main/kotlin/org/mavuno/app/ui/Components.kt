package org.mavuno.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mavuno.app.services
import org.mavuno.content.ContentPack

val LocalPack = staticCompositionLocalOf<ContentPack> { error("No content pack provided") }
val LocalLanguage = staticCompositionLocalOf { "sw" }

/** Every user-facing string comes from the active content pack by ID (PRD 10: no free text). */
@Composable
fun t(id: String, vararg params: Pair<String, Any>): String =
    LocalPack.current.text(id, params.associate { it.first to it.second.toString() })

private val Coffee = lightColorScheme(
    primary = Color(0xFF2F5D3A),
    onPrimary = Color.White,
    secondary = Color(0xFF6B4226),
    onSecondary = Color.White,
    background = Color(0xFFFAF6EE),
    surface = Color(0xFFFFFDF8),
    surfaceVariant = Color(0xFFEFE6D6),
    error = Color(0xFFB3261E),
    tertiary = Color(0xFFB86E00),
)

private val Big = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MavunoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Coffee, typography = Big) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** Standard scrolling screen with large padding. */
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/**
 * The screen's main prompt: shown large and played aloud on arrival, with a replay button (PRD 5, step 2).
 * The replay button is disabled when no clip exists for this ID yet.
 */
@Composable
fun SpokenPrompt(id: String, vararg params: Pair<String, Any>, autoPlay: Boolean = true) {
    val context = LocalContext.current
    val language = LocalLanguage.current
    val audio = context.services.audio
    val hasClip = remember(language, id) { audio.hasClip(language, id) }
    if (autoPlay) {
        LaunchedEffect(language, id) { audio.play(language, id) }
        DisposableEffect(language, id) { onDispose { audio.stop() } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t(id, *params), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { audio.play(language, id) }, enabled = hasClip) {
            Text("▶ " + t("common.replay"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false) {
    val colors = if (secondary) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    else ButtonDefaults.buttonColors()
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
fun Notice(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(14.dp))
    }
}
