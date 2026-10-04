package org.mavuno.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mavuno.app.services
import org.mavuno.fusion.FeatureCatalog
import org.mavuno.fusion.FeatureSource
import org.mavuno.fusion.FusionResult
import org.mavuno.fusion.RankedCause
import org.mavuno.fusion.ResultStatus
import org.mavuno.fusion.UNKNOWN_ANSWER

@Composable
fun InterviewScreen(index: Int, onNext: () -> Unit) {
    val services = LocalContext.current.services
    val (question, answers) = FeatureCatalog.questions.entries.elementAt(index)
    val session = services.session
    Screen {
        Text(t("interview.progress", "n" to index + 1, "total" to FeatureCatalog.questions.size), style = MaterialTheme.typography.bodyLarge)
        SpokenPrompt("question.$question")
        answers.forEach { a ->
            BigButton(t("answer.$question.$a"), onClick = { session.answers[question] = a; onNext() }, secondary = session.answers[question] != a)
        }
        BigButton(t("answer.unknown"), onClick = { session.answers[question] = UNKNOWN_ANSWER; onNext() }, secondary = true)
    }
}

@Composable
fun ResultScreen(onContinue: () -> Unit) {
    val services = LocalContext.current.services
    val session = services.session
    val result: FusionResult = remember(session) {
        session.result ?: run {
            val plot = services.prefs.plot
            session.site = plot?.let { services.regionalPack.lookup(it.lat, it.lon) }
            services.fusion.evaluate(session.fusionInput()).also { session.result = it }
        }
    }

    Screen {
        if (result.status == ResultStatus.OK) {
            SpokenPrompt("result.title")
            result.causes.forEach { CauseCard(it, reviewed = it.cause in services.reviewedCards) }
        } else {
            SpokenPrompt("abstain.message")
            result.abstainReasons.forEach { Notice(t(it.stringId), MaterialTheme.colorScheme.tertiaryContainer) }
            if (result.evidence.isNotEmpty()) {
                Text(t("result.could_see"), style = MaterialTheme.typography.titleMedium)
                result.evidence.filter { it.source != FeatureSource.PHOTO || it.feature == "photo.healthy" || it.value == "seen" }
                    .forEach { Text("• " + LocalPack.current.text(it.reason), style = MaterialTheme.typography.bodyLarge) }
            }
            if (result.unknownFeatures.isNotEmpty()) {
                Text(t("result.could_not_see"), style = MaterialTheme.typography.titleMedium)
                result.unknownFeatures.map(::unknownLabelId).distinct().forEach { Text("• " + t(it), style = MaterialTheme.typography.bodyLarge) }
            }
        }

        val site = session.site
        val through = site?.context?.dataThrough
        when {
            site == null || !site.insidePack -> Notice(t("result.outside_pack"))
            through != null -> Notice(t("result.data_through", "month" to through))
        }

        BigButton(t("common.next"), onContinue)
    }
}

/** Label for a feature the app could not establish. */
private fun unknownLabelId(feature: String): String = when {
    feature.startsWith("photo.") -> "feature.photos"
    feature in FeatureCatalog.questions -> "question.$feature"
    else -> "feature.$feature"
}

@Composable
private fun CauseCard(rc: RankedCause, reviewed: Boolean) {
    val pack = LocalPack.current
    var open by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(rc.nameId), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text(t(rc.label.stringId), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            rc.reasons.forEach { Text("• " + pack.text(it), style = MaterialTheme.typography.bodyLarge) }
            if (rc.cause.isSuspected) Notice(t("card.suspected_note"), MaterialTheme.colorScheme.tertiaryContainer)

            LinkButton((if (open) "▲ " else "▼ ") + t("card.steps_title")) { open = !open }
            if (open) {
                val card = pack.card(rc.cause)
                if (!reviewed) Notice(t("card.awaiting_review"), MaterialTheme.colorScheme.tertiaryContainer)
                Text(card.what, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider()
                Text(t("card.check_title"), style = MaterialTheme.typography.titleMedium)
                card.check.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                Text(t("card.steps_title"), style = MaterialTheme.typography.titleMedium)
                card.firstSteps.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                Text(t("card.officer_title"), style = MaterialTheme.typography.titleMedium)
                Text(card.contactOfficer, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConsentScreen(onDone: () -> Unit) {
    val services = LocalContext.current.services
    val session = services.session
    val scope = rememberCoroutineScope()
    var shareRecord by remember { mutableStateOf<Boolean?>(null) }
    var sharePhotos by remember { mutableStateOf<Boolean?>(null) }
    var savedShared by remember { mutableStateOf<Boolean?>(null) }

    Screen {
        val saved = savedShared
        if (saved != null) {
            SpokenPrompt(if (saved) "consent.saved_shared" else "consent.saved_local")
            BigButton(t("common.done"), onDone)
            return@Screen
        }
        SpokenPrompt("consent.title")
        Text(t("consent.record_question"), style = MaterialTheme.typography.titleMedium)
        YesNo(shareRecord) { shareRecord = it; if (!it) sharePhotos = null }
        if (shareRecord == true && session.photos.isNotEmpty()) {
            Text(t("consent.photos_question"), style = MaterialTheme.typography.titleMedium)
            YesNo(sharePhotos) { sharePhotos = it }
        }
        BigButton(
            text = t("setup.save"),
            enabled = shareRecord != null && !session.saved,
            onClick = {
                val result = session.result ?: return@BigButton
                val record = shareRecord == true
                scope.launch {
                    services.records.save(session, result, shareRecord = record, sharePhotos = record && sharePhotos == true)
                    savedShared = record
                }
            },
        )
    }
}

@Composable
private fun YesNo(value: Boolean?, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(t("consent.yes"), { onChange(true) }, Modifier.weight(1f), secondary = value != true)
        BigButton(t("consent.no"), { onChange(false) }, Modifier.weight(1f), secondary = value != false)
    }
}
