package org.mavuno.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
    Screen {
        SpokenPrompt("lang.choose")
        services.availableLanguages.forEach { lang ->
            BigButton(services.pack(lang).manifest.languageName, onClick = { onPick(lang) })
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
        SpokenPrompt("setup.title")
        OutlinedTextField(name, { name = it }, label = { Text(t("setup.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone, { phone = it }, label = { Text(t("setup.phone")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(coop, { coop = it }, label = { Text(t("setup.cooperative")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text(t("setup.mark_plot"), style = MaterialTheme.typography.bodyLarge)
        BigButton(
            text = if (locating) t("setup.locating") else t("setup.mark_plot_button"),
            enabled = !locating,
            secondary = plot != null,
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) locate() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
        )
        if (locationFailed) Notice(t("setup.location_failed"), MaterialTheme.colorScheme.errorContainer)
        plot?.let { p ->
            val inside = remember(p) { services.regionalPack.lookup(p.lat, p.lon).insidePack }
            Notice(t("setup.plot_marked") + " (%.5f, %.5f). ".format(p.lat, p.lon) + t(if (inside) "setup.plot_inside" else "result.outside_pack"))
        }

        Notice(t("setup.shared_phone_note"))

        BigButton(
            text = t("setup.save"),
            enabled = name.isNotBlank() && phone.isNotBlank() && plot != null,
            onClick = {
                services.prefs.profile = FarmerProfile(existing?.farmerId ?: Prefs.newId(), name.trim(), phone.trim(), coop.trim())
                services.prefs.plot = plot
                onDone()
            },
        )
    }
}
