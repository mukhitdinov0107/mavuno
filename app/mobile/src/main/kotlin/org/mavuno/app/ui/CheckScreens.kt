package org.mavuno.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(session.answers[question]) }
    var leaving by remember { mutableStateOf(false) }

    fun choose(answer: String) {
        if (leaving) return
        leaving = true
        picked = answer
        session.answers[question] = answer
        // A short beat so the person sees their choice land before the next question slides in.
        scope.launch { delay(260); onNext() }
    }

    Screen {
        QuestionProgress(index, FeatureCatalog.questions.size)
        Prompt("question.$question")
        if (answers.size == 4) {
            answers.chunked(2).forEach { row ->
                // Equal-height tiles even when one label wraps to two lines.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { a ->
                        AnswerTile(answerPic(question, a), t("answer.$question.$a"), picked == a, { choose(a) }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        } else {
            answers.forEach { a -> AnswerRow(answerPic(question, a), t("answer.$question.$a"), picked == a) { choose(a) } }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (picked == UNKNOWN_ANSWER) Palette.Leaf else Palette.Paper)
                .pressable { choose(UNKNOWN_ANSWER) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Pictogram(Pic.Question, Modifier.size(48.dp))
            Text(t("answer.unknown"), style = MaterialTheme.typography.titleLarge, color = if (picked == UNKNOWN_ANSWER) Palette.Paper else Palette.InkSoft)
        }
    }
}

@Composable
private fun QuestionProgress(index: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                val color by animateColorAsState(
                    when {
                        i < index -> Palette.Leaf
                        i == index -> Palette.Sun
                        else -> Palette.Line
                    },
                    tween(400),
                    label = "seg",
                )
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
            }
        }
        Text(t("interview.progress", "n" to index + 1, "total" to total), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
    }
}

@Composable
fun ResultScreen(onContinue: () -> Unit, onStartReal: () -> Unit, onHome: () -> Unit) {
    val services = LocalContext.current.services
    val session = services.session
    val result: FusionResult = remember(session) {
        session.result ?: run {
            if (session.site == null) session.site = services.prefs.plot?.let { services.regionalPack.lookup(it.lat, it.lon) }
            services.fusion.evaluate(session.fusionInput()).also { session.result = it }
        }
    }

    Screen {
        if (session.isExample) {
            Pill(t("example.badge"), Pic.Photos, Palette.CherryMist, Palette.Cherry)
            Text(t("example.notice"), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }

        if (result.status == ResultStatus.OK) {
            val top = result.causes.first()
            Verdict(top, reviewed = top.cause in services.reviewedCards)
            if (result.causes.size > 1) {
                Text(t("result.also_check"), style = MaterialTheme.typography.titleLarge)
                result.causes.drop(1).forEach { OtherCause(it, reviewed = it.cause in services.reviewedCards) }
            }
        } else {
            AbstainHeader()
            result.abstainReasons.forEach { Pill(t(it.stringId), bg = Palette.SunMist) }
            val seen = result.evidence.filter { it.source != FeatureSource.PHOTO || it.feature == "photo.healthy" || it.value == "seen" }
            if (seen.isNotEmpty()) {
                Text(t("result.could_see"), style = MaterialTheme.typography.titleLarge)
                seen.forEach { EvidenceRow(reasonPic(it.reason.id), LocalPack.current.text(it.reason)) }
            }
            if (result.unknownFeatures.isNotEmpty()) {
                Text(t("result.could_not_see"), style = MaterialTheme.typography.titleLarge)
                result.unknownFeatures.map(::unknownLabel).distinctBy { it.first }.forEach { (id, pic) -> EvidenceRow(pic, t(id), muted = true) }
            }
        }

        val site = session.site
        val through = site?.context?.dataThrough
        when {
            site == null || !site.insidePack -> Pill(t("result.outside_pack"), Pic.Drop, Palette.SkyMist)
            through != null -> Pill(t("result.data_through", "month" to through), Pic.Drop, Palette.SkyMist)
        }

        if (session.isExample) {
            BigButton(t("example.start_real"), onStartReal, pic = Pic.Camera)
            LinkButton(t("common.done"), onHome)
        } else {
            BigButton(t("common.next"), onContinue)
        }
    }
}

/** The answer, big: cause name, a three-step confidence meter, the reasons, and the advice card. */
@Composable
private fun Verdict(rc: RankedCause, reviewed: Boolean) {
    val pack = LocalPack.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.LeafMist).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("result.title"), style = MaterialTheme.typography.titleMedium, color = Palette.InkSoft, modifier = Modifier.weight(1f))
            SpeakButton("result.title")
        }
        Text(t(rc.nameId), style = MaterialTheme.typography.displaySmall, color = Palette.LeafDeep)
        ConfidenceMeter(rc.label.id)
        if (rc.cause.isSuspected) Pill(t("card.suspected_note"), Pic.Officer, Palette.SunMist)
        Text(t("result.why"), style = MaterialTheme.typography.titleMedium, color = Palette.InkSoft)
        StaggeredColumn(rc.reasons.map { { EvidenceRow(reasonPic(it.id), pack.text(it)) } })
    }
    AdviceCard(rc, reviewed, startOpen = true)
}

@Composable
private fun OtherCause(rc: RankedCause, reviewed: Boolean) {
    val pack = LocalPack.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Paper).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(t(rc.nameId), style = MaterialTheme.typography.titleLarge)
        ConfidenceMeter(rc.label.id)
        rc.reasons.forEach { EvidenceRow(reasonPic(it.id), pack.text(it)) }
        AdviceCard(rc, reviewed, startOpen = false)
    }
}

/** Likely fills three segments, possible two, worth checking one. Fills one by one on arrival. */
@Composable
fun ConfidenceMeter(labelId: String) {
    val filled = when (labelId) { "likely" -> 3; "possible" -> 2; else -> 1 }
    val still = reducedMotion()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val fill = remember { Animatable(if (still) 1f else 0f) }
                LaunchedEffect(Unit) { if (!still && i < filled) { delay(250L + i * 220L); fill.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) } }
                Box(Modifier.size(width = 34.dp, height = 14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Line)) {
                    if (i < filled) Box(Modifier.matchParentSizeScaled(fill.value).clip(RoundedCornerShape(7.dp)).background(if (filled == 3) Palette.Leaf else Palette.Sun))
                }
            }
        }
        Text(t("confidence.$labelId"), style = MaterialTheme.typography.titleMedium)
    }
}

private fun Modifier.matchParentSizeScaled(f: Float) = this
    .size(width = 34.dp, height = 14.dp)
    .graphicsLayer { scaleX = f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }

/** Reveals children one after another, once, when the result first appears. */
@Composable
private fun StaggeredColumn(items: List<@Composable () -> Unit>) {
    val still = reducedMotion()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, item ->
            var shown by remember { mutableStateOf(still) }
            LaunchedEffect(Unit) { delay(500L + i * 180L); shown = true }
            AnimatedVisibility(shown, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) { item() }
        }
    }
}

@Composable
private fun AdviceCard(rc: RankedCause, reviewed: Boolean, startOpen: Boolean) {
    val pack = LocalPack.current
    var open by remember { mutableStateOf(startOpen) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.SunMist)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
                Pictogram(Pic.SoilLayers, Modifier.size(32.dp))
            }
            Text(t("card.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chev")
            Chevron(Modifier.size(28.dp).graphicsLayer { rotationZ = turn })
        }
        if (open) {
            val card = pack.card(rc.cause)
            if (!reviewed) Pill(t("card.awaiting_review"), bg = Palette.Paper, fg = Palette.Soil)
            Text(card.what, style = MaterialTheme.typography.bodyLarge)
            Section(Pic.Leaf, t("card.check_title"), card.check)
            Section(Pic.Scissors(1f), t("card.steps_title"), card.firstSteps)
            Section(Pic.Officer, t("card.officer_title"), listOf(card.contactOfficer))
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply { moveTo(w * 0.2f, h * 0.38f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.8f, h * 0.38f) },
            Palette.Ink,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun Section(pic: Pic, title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) { Pictogram(pic, Modifier.size(24.dp)) }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        lines.forEach { line ->
            Row(Modifier.padding(start = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 10.dp).size(7.dp).clip(CircleShape).background(Palette.Leaf))
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun AbstainHeader() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.SunMist).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val still = reducedMotion()
        val pop = remember { Animatable(if (still) 1f else 0.6f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        Box(Modifier.size(120.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
            Pictogram(Pic.Officer, Modifier.size(88.dp))
        }
        Prompt("abstain.message")
    }
}

private fun unknownLabel(feature: String): Pair<String, Pic> = when {
    feature.startsWith("photo.") -> "feature.photos" to Pic.Camera
    feature in FeatureCatalog.questions -> "question.$feature" to Pic.Chat
    feature.startsWith("rain_") -> "feature.$feature" to Pic.Drop
    else -> "feature.$feature" to Pic.SoilLayers
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
            Saved(saved, onDone)
            return@Screen
        }
        Prompt("consent.title")
        ConsentQuestion(Pic.Send, t("consent.record_question"), shareRecord) { shareRecord = it; if (!it) sharePhotos = null }
        AnimatedVisibility(shareRecord == true && session.photos.isNotEmpty(), enter = fadeIn() + expandVertically()) {
            ConsentQuestion(Pic.Photos, t("consent.photos_question"), sharePhotos) { sharePhotos = it }
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
private fun ConsentQuestion(pic: Pic, question: String, value: Boolean?, onChange: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.LeafMist).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) { Pictogram(pic, Modifier.size(40.dp)) }
            Text(question, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(t("consent.yes"), value == true, Modifier.weight(1f)) { onChange(true) }
            Choice(t("consent.no"), value == false, Modifier.weight(1f)) { onChange(false) }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Leaf else Palette.Paper, label = "choice")
    val fg by animateColorAsState(if (selected) Palette.Paper else Palette.Ink, label = "choiceFg")
    Box(
        modifier.height(60.dp).clip(RoundedCornerShape(18.dp)).background(bg).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = fg) }
}

@Composable
private fun Saved(shared: Boolean, onDone: () -> Unit) {
    val still = reducedMotion()
    val pop = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(160.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
            Pictogram(if (shared) Pic.CloudUp else Pic.Check, Modifier.size(160.dp))
        }
    }
    Prompt(if (shared) "consent.saved_shared" else "consent.saved_local")
    BigButton(t("common.done"), onDone)
}
