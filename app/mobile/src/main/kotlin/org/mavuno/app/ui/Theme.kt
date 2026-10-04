package org.mavuno.app.ui

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.mavuno.app.R

/**
 * Colours of a Nyeri coffee plot, on white so the screen stays readable in full sun:
 * glossy arabica leaf, ripe cherry, highland sun, red volcanic soil, rain sky.
 */
object Palette {
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14261B)
    val InkSoft = Color(0xFF4A5A50)
    val Leaf = Color(0xFF1F7A4A)
    val LeafDeep = Color(0xFF0F4D2C)
    val LeafMist = Color(0xFFE5F3E9)
    val Cherry = Color(0xFFD7263D)
    val CherryMist = Color(0xFFFCE4E7)
    val Sun = Color(0xFFFFC23C)
    val SunMist = Color(0xFFFFF3D3)
    val Soil = Color(0xFF9A3E1C)
    val Sky = Color(0xFF2C7FB8)
    val SkyMist = Color(0xFFE6F1FA)
    val Line = Color(0xFFDCE5DF)
}

/** Atkinson Hyperlegible: drawn by the Braille Institute for readers with low vision. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = Atkinson, fontSize = 19.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
)

private val Scheme = lightColorScheme(
    primary = Palette.Leaf,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.LeafMist,
    onPrimaryContainer = Palette.LeafDeep,
    secondary = Palette.Soil,
    tertiary = Palette.Sun,
    tertiaryContainer = Palette.SunMist,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.LeafMist,
    onSurfaceVariant = Palette.InkSoft,
    error = Palette.Cherry,
    errorContainer = Palette.CherryMist,
    outline = Palette.Line,
)

@Composable
fun MavunoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type) {
        Surface(color = Palette.Paper, contentColor = Palette.Ink, modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** True when the phone has animations turned off; ambient motion is skipped then. */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
