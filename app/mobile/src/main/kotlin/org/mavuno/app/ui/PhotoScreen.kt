package org.mavuno.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mavuno.app.BuildConfig
import org.mavuno.app.Prefs
import org.mavuno.app.camera.PhotoQuality
import org.mavuno.app.camera.QualityGate
import org.mavuno.app.ml.LeafClassifier
import org.mavuno.app.services
import org.mavuno.app.session.CapturedPhoto
import org.mavuno.app.session.CheckSession
import org.mavuno.fusion.LeafClass
import org.mavuno.fusion.PhotoObservation
import java.io.File

@Composable
fun PhotoScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val session = services.session
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Screen {
        Prompt("photos.instruction")
        if (!services.classifier.available) Pill(t("photos.model_missing"), Pic.Leaf, Palette.SunMist)
        if (granted) CameraBlock(session, onDone) else {
            Pill(t("permission.camera_needed"), Pic.Camera, Palette.SunMist)
            BigButton(t("permission.allow"), { permission.launch(Manifest.permission.CAMERA) }, pic = Pic.Camera)
        }
        val enough = session.photos.size >= CheckSession.MIN_LEAVES && session.treesPhotographed >= CheckSession.MIN_TREES
        if (!enough) LinkButton(t("photos.skip"), onDone)
    }
}

/** [at] makes a repeated message (two blurry photos in a row) count as new, so it shows again. */
private data class Feedback(val id: String, val ok: Boolean, val at: Long = System.nanoTime())

@Composable
private fun CameraBlock(session: CheckSession, onDone: () -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    LaunchedEffect(lifecycle) { controller.bindToLifecycle(lifecycle) }

    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var lastFeedback by remember { mutableStateOf(Feedback("photos.ok", true)) }
    val flash = remember { Animatable(0f) }
    /** Debug builds without a model let the tester label a photo by hand, so the flow can be exercised end to end. */
    var pendingDebugLabel by remember { mutableStateOf<Pair<File, Int>?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let { lastFeedback = it; delay(2600); feedback = null }
    }

    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp)).background(Color.Black)) {
        AndroidView(
            factory = {
                // TextureView-backed so the preview clips to this rounded box inside a scrolling column.
                PreviewView(it).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        LeafGuide()
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
        AnimatedVisibility(
            visible = feedback != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
        ) {
            val f = feedback ?: lastFeedback
            Pill(t(f.id), if (f.ok) Pic.Check else Pic.Camera, if (f.ok) Palette.LeafMist else Palette.SunMist)
        }
    }

    LeafTray(session.photos.size)
    TreeRow(session)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        SideAction(Pic.Tree(0.8f), t("photos.next_tree"), enabled = session.photos.any { it.treeIndex == session.treeIndex }) {
            session.treeIndex += 1
            feedback = null
        }
        Shutter(enabled = !busy) {
            busy = true
            scope.launch { flash.snapTo(0.8f); flash.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
            controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toUprightBitmap()
                    scope.launch {
                        val outcome = withContext(Dispatchers.Default) { processPhoto(bitmap, services.classifier, session.photoDir(context.filesDir)) }
                        busy = false
                        when (outcome) {
                            is Outcome.Rejected -> feedback = Feedback(outcome.quality.stringId!!, false)
                            is Outcome.Kept -> {
                                val obs = outcome.observation
                                if (obs == null && BuildConfig.DEBUG) {
                                    pendingDebugLabel = outcome.file to session.treeIndex
                                } else {
                                    session.photos += CapturedPhoto(outcome.photoId, outcome.file, session.treeIndex, obs?.leafClass, obs?.prob)
                                    val accepted = obs != null && obs.leafClass != LeafClass.OTHER && obs.prob >= services.weights.config.photoAcceptProb
                                    feedback = if (obs == null || accepted) Feedback("photos.ok", true) else Feedback("photos.retake_no_leaf", false)
                                }
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                }
            })
        }
        val enough = session.photos.size >= CheckSession.MIN_LEAVES && session.treesPhotographed >= CheckSession.MIN_TREES
        SideAction(Pic.Check, t("common.next"), enabled = enough, onClick = onDone)
    }

    pendingDebugLabel?.let { (file, tree) ->
        DebugLabelDialog(
            onPick = { cls ->
                session.photos += CapturedPhoto(file.nameWithoutExtension, file, tree, cls, 0.9)
                feedback = Feedback("photos.ok", true)
                pendingDebugLabel = null
            },
            onSkip = {
                session.photos += CapturedPhoto(file.nameWithoutExtension, file, tree, null, null)
                pendingDebugLabel = null
            },
        )
    }
}

/** Five leaf slots that fill, with a bounce, as good photos come in. */
@Composable
private fun LeafTray(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(CheckSession.MIN_LEAVES) { i ->
                val filled = i < count
                val scale by animateFloatAsState(if (filled) 1f else 0.7f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow), label = "slot")
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (filled) Palette.LeafMist else Palette.Paper)
                        .border(2.dp, if (filled) Palette.Leaf else Palette.Line, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Pictogram(Pic.Leaf, Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (filled) 1f else 0.18f })
                }
            }
        }
        Text(t("photos.leaves_count", "n" to count, "total" to CheckSession.MIN_LEAVES), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TreeRow(session: CheckSession) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(maxOf(CheckSession.MIN_TREES, session.treeIndex)) { i ->
            val tree = i + 1
            val done = session.photos.any { it.treeIndex == tree }
            val current = tree == session.treeIndex
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (current) Palette.SunMist else if (done) Palette.LeafMist else Palette.Paper)
                    .border(2.dp, if (current) Palette.Sun else if (done) Palette.Leaf else Palette.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Pictogram(Pic.Tree(0.75f), Modifier.size(36.dp).graphicsLayer { alpha = if (done || current) 1f else 0.3f }) }
        }
        Text(t("photos.tree", "n" to session.treeIndex), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val label = t("photos.take")
    Box(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Palette.Leaf)
            .pressable(enabled, onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(if (enabled) Palette.Paper else Palette.Line), contentAlignment = Alignment.Center) {
            Pictogram(Pic.Camera, Modifier.size(44.dp))
        }
    }
}

@Composable
private fun SideAction(pic: Pic, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) Palette.LeafMist else Palette.Paper)
            .pressable(enabled, onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Pictogram(pic, Modifier.size(40.dp).graphicsLayer { alpha = if (enabled) 1f else 0.3f })
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) Palette.Ink else Palette.InkSoft, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** A coffee-leaf outline to frame one leaf. */
@Composable
private fun LeafGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val leaf = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            cubicTo(w * 0.86f, h * 0.28f, w * 0.86f, h * 0.72f, w * 0.5f, h * 0.9f)
            cubicTo(w * 0.14f, h * 0.72f, w * 0.14f, h * 0.28f, w * 0.5f, h * 0.1f)
            close()
        }
        drawPath(leaf, Color.White.copy(alpha = 0.9f), style = Stroke(width = 5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f))))
    }
}

private sealed interface Outcome {
    data class Rejected(val quality: PhotoQuality) : Outcome
    data class Kept(val photoId: String, val file: File, val observation: PhotoObservation?) : Outcome
}

/** Quality gate, then classify, then save a downscaled JPEG in app-private storage. */
private fun processPhoto(bitmap: Bitmap, classifier: LeafClassifier, dir: File): Outcome {
    val quality = QualityGate.check(bitmap)
    if (quality != PhotoQuality.OK) return Outcome.Rejected(quality)
    val photoId = Prefs.newId()
    val file = dir.resolve("$photoId.jpg")
    val scale = 1600f / maxOf(bitmap.width, bitmap.height)
    val stored = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return Outcome.Kept(photoId, file, classifier.classify(bitmap))
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    close()
    if (rotation == 0) return raw
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}

@Composable
private fun DebugLabelDialog(onPick: (LeafClass) -> Unit, onSkip: () -> Unit) {
    // Debug builds only; never shown in release, so these labels are not part of the content packs.
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("DEBUG: no model. Label this photo") },
        text = { Column { LeafClass.entries.forEach { cls -> TextButton(onClick = { onPick(cls) }) { Text(cls.id) } } } },
        confirmButton = { TextButton(onClick = onSkip) { Text("unlabelled") } },
    )
}
