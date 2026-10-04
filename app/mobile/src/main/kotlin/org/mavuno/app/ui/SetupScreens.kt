package org.mavuno.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.mavuno.app.FarmerProfile
import org.mavuno.app.Plot
import org.mavuno.app.Prefs
import org.mavuno.app.location.PlotLocator
import org.mavuno.app.services

@Composable
fun LanguageScreen(onPick: (String) -> Unit) {
    val services = LocalContext.current.services
    val still = reducedMotion()
    val grow = remember { Animatable(if (still) 1f else 0.3f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessVeryLow)) }
    Screen {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).graphicsLayer { scaleX = grow.value; scaleY = grow.value }.clip(CircleShape).background(Palette.LeafMist), contentAlignment = Alignment.Center) {
                Pictogram(Pic.Tree(1f), Modifier.size(120.dp))
            }
        }
        Text(t("app.name"), style = MaterialTheme.typography.displaySmall, color = Palette.LeafDeep, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Prompt("lang.choose")
        services.languagesInOrder.forEach { lang ->
            BigButton(services.pack(lang).manifest.languageName, { onPick(lang) }, tone = ButtonTone.Quiet, pic = Pic.Chat)
        }
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val scope = rememberCoroutineScope()
    val existing = services.prefs.profile
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var coop by remember { mutableStateOf(existing?.cooperative.orEmpty()) }
    var plot by remember { mutableStateOf(services.prefs.plot) }
    var locating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        locationFailed = false
        scope.launch {
            val fix = PlotLocator.currentLocation(context)
            locating = false
            if (fix == null) locationFailed = true else plot = Plot(plot?.plotId ?: Prefs.newId(), fix.latitude, fix.longitude)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else locationFailed = true
    }

    Screen {
        Prompt("setup.title")
        Field(name, { name = it }, t("setup.name"))
        Field(phone, { phone = it }, t("setup.phone"), KeyboardType.Phone)
        Field(coop, { coop = it }, t("setup.cooperative"))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).border(2.dp, Palette.Line, RoundedCornerShape(28.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.LeafMist), contentAlignment = Alignment.Center) {
                    Pictogram(Pic.SoilLayers, Modifier.size(44.dp))
                }
                Text(t("setup.mark_plot"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            BigButton(
                text = if (locating) t("setup.locating") else t("setup.mark_plot_button"),
                enabled = !locating,
                tone = if (plot != null) ButtonTone.Quiet else ButtonTone.Primary,
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) locate() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
            )
            if (locationFailed) Pill(t("setup.location_failed"), bg = Palette.CherryMist, fg = Palette.Cherry)
            plot?.let { p ->
                val inside = remember(p) { services.regionalPack.lookup(p.lat, p.lon).insidePack }
                Pill(t("setup.plot_marked") + "  %.4f, %.4f".format(p.lat, p.lon), Pic.Check, Palette.LeafMist)
                Text(t(if (inside) "setup.plot_inside" else "result.outside_pack"), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.SkyMist).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Pictogram(Pic.Officer, Modifier.size(40.dp))
            Text(t("setup.shared_phone_note"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }

        BigButton(
            text = t("setup.save"),
            enabled = name.isNotBlank() && phone.isNotBlank() && plot != null,
            pic = Pic.Check,
            onClick = {
                services.prefs.profile = FarmerProfile(existing?.farmerId ?: Prefs.newId(), name.trim(), phone.trim(), coop.trim())
                services.prefs.plot = plot
                onDone()
            },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Leaf, unfocusedBorderColor = Palette.Line, focusedLabelColor = Palette.Leaf),
        modifier = Modifier.fillMaxWidth(),
    )
}
