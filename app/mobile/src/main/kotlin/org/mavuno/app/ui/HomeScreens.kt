package org.mavuno.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mavuno.app.data.FieldRecord
import org.mavuno.app.data.RecordEntity
import org.mavuno.app.data.SyncState
import org.mavuno.app.services
import org.mavuno.fusion.Cause
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onCheck: () -> Unit, onExample: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onOpenRecord: (String) -> Unit) {
    val services = LocalContext.current.services
    val queued by remember { services.records.observeQueuedCount() }.collectAsState(initial = 0)
    val records by remember { services.records.observeAll() }.collectAsState(initial = emptyList())
    Screen {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pictogram(Pic.Leaf, Modifier.size(30.dp))
            Text(t("app.name"), style = MaterialTheme.typography.titleLarge, color = Palette.LeafDeep, modifier = Modifier.weight(1f))
            IconButton(Pic.Clock, t("home.history"), onHistory)
            IconButton(Pic.Sliders, t("home.settings"), onSettings)
        }

        Hero(onCheck)

        Text(
            t("home.tagline"),
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        if (queued > 0) Pill(t("home.reports_waiting", "n" to queued), Pic.CloudUp, Palette.SkyMist)
        else if (records.isNotEmpty()) Pill(t("home.all_sent"), Pic.Check, Palette.LeafMist)

        records.firstOrNull()?.let { last ->
            Text(t("home.last_check"), style = MaterialTheme.typography.titleMedium, color = Palette.InkSoft)
            RecordRow(last) { onOpenRecord(last.recordId) }
        }

        BigButton(t("home.try_example"), onExample, tone = ButtonTone.Quiet, pic = Pic.Photos)
    }
}

/**
 * The one orchestrated moment: leaf, question and rain fly in and settle around the check button —
 * three kinds of evidence, one answer. After that the button only breathes.
 */
@Composable
private fun Hero(onCheck: () -> Unit) {
    val still = reducedMotion()
    val arrive = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!still) {
            delay(120)
            arrive.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 90f))
        }
    }
    val breathe = if (still) 1f else rememberInfiniteTransition(label = "breathe")
        .animateFloat(1f, 1.04f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe").value

    val badges = listOf(
        Triple(Pic.Leaf, Palette.LeafMist, Offset(-118f, -96f)),
        Triple(Pic.Chat, Palette.SunMist, Offset(118f, -96f)),
        Triple(Pic.Drop, Palette.SkyMist, Offset(0f, 136f)),
    )
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
        val a = arrive.value
        // Dotted threads from each badge into the button.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = a.coerceIn(0f, 1f) }) {
            val c = Offset(size.width / 2, size.height / 2)
            badges.forEach { (_, _, p) ->
                val end = c + Offset(with(density) { p.x.dp.toPx() }, with(density) { p.y.dp.toPx() })
                drawLine(Palette.Line, c, end, strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 14f)), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Box(Modifier.size(232.dp).graphicsLayer { scaleX = 2.1f - breathe; scaleY = 2.1f - breathe }.clip(CircleShape).background(Palette.LeafMist))
        badges.forEach { (pic, bg, p) ->
            val k = 1f + (1f - a) * 1.8f
            Box(
                Modifier
                    .offset((p.x * k).dp, (p.y * k).dp)
                    .graphicsLayer { alpha = a.coerceIn(0f, 1f); rotationZ = (1f - a) * 40f }
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) { Pictogram(pic, Modifier.size(44.dp)) }
        }
        Column(
            Modifier
                .size(200.dp)
                .graphicsLayer { val sc = breathe * (0.8f + 0.2f * a); scaleX = sc; scaleY = sc }
                .clip(CircleShape)
                .background(Palette.Leaf)
                .pressable(onClick = onCheck),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
                Pictogram(Pic.Camera, Modifier.size(40.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(t("home.check_farm"), style = MaterialTheme.typography.titleLarge, color = Palette.Paper, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun RecordRow(r: RecordEntity, onClick: () -> Unit) {
    val language = LocalLanguage.current
    val needsHuman = r.status == "needs_human" || r.topCause == null
    val title = if (needsHuman) t("abstain.title") else t("cause.${r.topCause}.name")
    val (syncPic, syncBg) = when (r.syncState) {
        SyncState.QUEUED -> Pic.CloudUp to Palette.SkyMist
        SyncState.SENT -> Pic.Check to Palette.LeafMist
        else -> Pic.Photos to Palette.Line
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (needsHuman) Palette.SunMist else Palette.LeafMist)
            .pressable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
            Pictogram(if (needsHuman) Pic.Officer else Pic.Leaf, Modifier.size(38.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale(language)).format(Date(r.createdAt)), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            if (r.reply != null) Text(t("history.reply"), style = MaterialTheme.typography.labelMedium, color = Palette.Leaf)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(syncBg), contentAlignment = Alignment.Center) {
            Pictogram(syncPic, Modifier.size(24.dp))
        }
    }
}

@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onCheck: () -> Unit) {
    val services = LocalContext.current.services
    val records by remember { services.records.observeAll() }.collectAsState(initial = emptyList())
    Screen {
        Prompt("home.history")
        if (records.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Pictogram(Pic.Clock, Modifier.size(120.dp))
                Text(t("history.empty"), style = MaterialTheme.typography.titleLarge, color = Palette.InkSoft)
            }
            BigButton(t("home.check_farm"), onCheck, pic = Pic.Camera)
        }
        records.forEach { r -> RecordRow(r) { onOpen(r.recordId) } }
    }
}

@Composable
fun RecordScreen(recordId: String) {
    val services = LocalContext.current.services
    val entity by produceState<RecordEntity?>(null, recordId) { value = services.records.byId(recordId) }
    val r = entity ?: return
    val record = remember(r.json) { FieldRecord.json.decodeFromString(FieldRecord.serializer(), r.json) }
    val language = LocalLanguage.current
    Screen {
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale(language)).format(Date(r.createdAt)), style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
        if (record.result.status == "needs_human") {
            AbstainHeader()
            record.result.abstainReasons.forEach { Pill(t("abstain.$it"), bg = Palette.SunMist) }
        } else {
            record.result.causes.forEachIndexed { i, c ->
                Text(t("cause.${c.id}.name"), style = if (i == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                ConfidenceMeter(c.label)
                c.reasons.forEach { reason ->
                    EvidenceRow(reasonPic(reason.id), t(reason.id, *reason.params.map { it.key to it.value }.toTypedArray()))
                }
                if (Cause.fromId(c.id).isSuspected) Pill(t("card.suspected_note"), Pic.Officer, Palette.SunMist)
            }
        }
        Pill(t("history.state.${r.syncState}"), if (r.syncState == SyncState.SENT) Pic.Check else Pic.CloudUp, Palette.SkyMist)
        r.reply?.let {
            Text(t("history.reply"), style = MaterialTheme.typography.titleLarge)
            // The officer's own words, shown as written.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.LeafMist).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pictogram(Pic.Officer, Modifier.size(44.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SettingsScreen(onLanguage: (String) -> Unit, onDeleted: () -> Unit) {
    val services = LocalContext.current.services
    var confirm by remember { mutableStateOf(false) }
    Screen {
        Prompt("home.settings")
        Text(t("settings.language"), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            services.languagesInOrder.forEach { lang ->
                AnswerTile(
                    pic = Pic.Chat,
                    label = services.pack(lang).manifest.languageName,
                    selected = lang == LocalLanguage.current,
                    onClick = { onLanguage(lang) },
                    modifier = Modifier.weight(1f),
                    picSize = 44.dp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton(t("settings.delete"), { confirm = true }, tone = ButtonTone.Danger, pic = Pic.Trash)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Pictogram(Pic.Trash, Modifier.size(48.dp)) },
            title = { Text(t("settings.delete"), style = MaterialTheme.typography.titleLarge) },
            text = { Text(t("settings.delete_confirm"), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { confirm = false; services.deleteEverything(); onDeleted() }) {
                    Text(t("settings.delete_yes"), style = MaterialTheme.typography.labelLarge, color = Palette.Cherry)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("common.cancel"), style = MaterialTheme.typography.labelLarge) } },
            containerColor = Palette.Paper,
        )
    }
}

/** Source picture for a reason ID: photo, interview, rain or soil. */
fun reasonPic(reasonId: String): Pic {
    val feature = reasonId.removePrefix("reason.").substringBeforeLast(".")
    return when {
        feature.startsWith("photo.") -> Pic.Camera
        feature.startsWith("rain_") -> Pic.Drop
        feature.startsWith("soil_") -> Pic.SoilLayers
        else -> Pic.Chat
    }
}
