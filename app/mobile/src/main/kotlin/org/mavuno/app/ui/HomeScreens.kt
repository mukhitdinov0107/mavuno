package org.mavuno.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.mavuno.app.data.FieldRecord
import org.mavuno.app.data.RecordEntity
import org.mavuno.app.services
import org.mavuno.fusion.Cause
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onCheck: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val services = LocalContext.current.services
    val queued by remember { services.records.observeQueuedCount() }.collectAsState(initial = 0)
    Screen {
        Text(t("app.name"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        SpokenPrompt("home.check_farm")
        BigButton(t("home.check_farm"), onCheck)
        Notice(if (queued > 0) t("home.reports_waiting", "n" to queued) else t("home.all_sent"))
        BigButton(t("home.history"), onHistory, secondary = true)
        BigButton(t("home.settings"), onSettings, secondary = true)
    }
}

@Composable
fun HistoryScreen(onOpen: (String) -> Unit) {
    val services = LocalContext.current.services
    val records by remember { services.records.observeAll() }.collectAsState(initial = emptyList())
    Screen {
        SpokenPrompt("home.history")
        if (records.isEmpty()) Notice(t("history.empty"))
        records.forEach { r -> HistoryRow(r) { onOpen(r.recordId) } }
    }
}

@Composable
private fun HistoryRow(r: RecordEntity, onClick: () -> Unit) {
    val title = if (r.status == "needs_human") t("abstain.title") else r.topCause?.let { t("cause.$it.name") } ?: t("abstain.title")
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale(LocalLanguage.current)).format(Date(r.createdAt)), style = MaterialTheme.typography.bodyMedium)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(t("history.state.${r.syncState}"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            if (r.reply != null) Text("✉ " + t("history.reply"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RecordScreen(recordId: String) {
    val services = LocalContext.current.services
    val entity by produceState<RecordEntity?>(null, recordId) { value = services.records.byId(recordId) }
    val r = entity ?: return
    val record = remember(r.json) { FieldRecord.json.decodeFromString(FieldRecord.serializer(), r.json) }
    Screen {
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale(LocalLanguage.current)).format(Date(r.createdAt)), style = MaterialTheme.typography.bodyLarge)
        if (record.result.status == "needs_human") {
            SpokenPrompt("abstain.message", autoPlay = false)
            record.result.abstainReasons.forEach { Text("• " + t("abstain.$it"), style = MaterialTheme.typography.bodyLarge) }
        } else {
            record.result.causes.forEach { c ->
                Text(t("cause.${c.id}.name") + " — " + t("confidence.${c.label}"), style = MaterialTheme.typography.titleMedium)
                c.reasons.forEach { reason -> Text("• " + t(reason.id, *reason.params.map { it.key to it.value }.toTypedArray()), style = MaterialTheme.typography.bodyLarge) }
                if (Cause.fromId(c.id).isSuspected) Notice(t("card.suspected_note"))
            }
        }
        Notice(t("history.state.${r.syncState}"))
        r.reply?.let {
            Text(t("history.reply"), style = MaterialTheme.typography.titleMedium)
            // The officer's own words, shown as written.
            Notice(it, MaterialTheme.colorScheme.primaryContainer)
        }
    }
}

@Composable
fun SettingsScreen(onLanguage: (String) -> Unit, onDeleted: () -> Unit) {
    val services = LocalContext.current.services
    var confirm by remember { mutableStateOf(false) }
    Screen {
        SpokenPrompt("home.settings")
        Text(t("settings.language"), style = MaterialTheme.typography.titleMedium)
        services.availableLanguages.forEach { lang ->
            BigButton(services.pack(lang).manifest.languageName, onClick = { onLanguage(lang) }, secondary = lang != LocalLanguage.current)
        }
        BigButton(t("settings.delete"), onClick = { confirm = true }, secondary = true)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t("settings.delete")) },
            text = { Text(t("settings.delete_confirm"), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { confirm = false; services.deleteEverything(); onDeleted() }) {
                    Text(t("settings.delete_yes"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("common.cancel")) } },
        )
    }
}
