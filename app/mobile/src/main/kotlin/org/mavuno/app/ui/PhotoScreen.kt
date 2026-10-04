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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
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
        SpokenPrompt("photos.instruction")
        if (!services.classifier.available) Notice(t("photos.model_missing"), MaterialTheme.colorScheme.tertiaryContainer)
        if (granted) CameraBlock(session) else {
            Notice(t("permission.camera_needed"))
            BigButton(t("permission.allow"), { permission.launch(Manifest.permission.CAMERA) })
        }
        val enough = session.photos.size >= CheckSession.MIN_LEAVES && session.treesPhotographed >= CheckSession.MIN_TREES
        BigButton(t("common.next"), onDone, enabled = enough)
        if (!enough) LinkButton(t("photos.skip"), onDone)
    }
}

@Composable
private fun CameraBlock(session: CheckSession) {
    val context = LocalContext.current
    val services = context.services
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    LaunchedEffect(lifecycle) { controller.bindToLifecycle(lifecycle) }

    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    /** Debug builds without a model let the tester label a photo by hand, so the flow can be exercised end to end. */
    var pendingDebugLabel by remember { mutableStateOf<Pair<File, Int>?>(null) }

    // TextureView-backed preview so it clips to its box inside a scrolling Compose column.
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()) {
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        FramingGuide()
    }

    Text(t("photos.tree", "n" to session.treeIndex), style = MaterialTheme.typography.titleMedium)
    Text(t("photos.progress", "leaves" to session.photos.size, "trees" to session.treesPhotographed), style = MaterialTheme.typography.bodyLarge)
    feedback?.let { (id, ok) -> Notice(t(id), if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(
            text = t("photos.take"),
            enabled = !busy,
            modifier = Modifier.weight(1f),
            onClick = {
                busy = true
                controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.toUprightBitmap()
                        scope.launch {
                            val outcome = withContext(Dispatchers.Default) { processPhoto(bitmap, services.classifier, session.photoDir(context.filesDir)) }
                            busy = false
                            when (outcome) {
                                is Outcome.Rejected -> feedback = outcome.quality.stringId!! to false
                                is Outcome.Kept -> {
                                    val obs = outcome.observation
                                    if (obs == null && BuildConfig.DEBUG) {
                                        pendingDebugLabel = outcome.file to session.treeIndex
                                    } else {
                                        session.photos += CapturedPhoto(outcome.photoId, outcome.file, session.treeIndex, obs?.leafClass, obs?.prob)
                                        val accepted = obs != null && obs.leafClass != LeafClass.OTHER && obs.prob >= services.weights.config.photoAcceptProb
                                        feedback = if (obs == null || accepted) "photos.ok" to true else "photos.retake_no_leaf" to false
                                    }
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                    }
                })
            },
        )
        BigButton(
            text = t("photos.next_tree"),
            secondary = true,
            enabled = session.photos.any { it.treeIndex == session.treeIndex },
            modifier = Modifier.weight(1f),
            onClick = { session.treeIndex += 1; feedback = null },
        )
    }

    pendingDebugLabel?.let { (file, tree) ->
        DebugLabelDialog(
            onPick = { cls ->
                session.photos += CapturedPhoto(file.nameWithoutExtension, file, tree, cls, 0.9)
                feedback = "photos.ok" to true
                pendingDebugLabel = null
            },
            onSkip = {
                session.photos += CapturedPhoto(file.nameWithoutExtension, file, tree, null, null)
                pendingDebugLabel = null
            },
        )
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
private fun FramingGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val inset = size.minDimension * 0.12f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(32f, 32f),
            style = Stroke(width = 6f),
        )
    }
}

@Composable
private fun DebugLabelDialog(onPick: (LeafClass) -> Unit, onSkip: () -> Unit) {
    // Debug builds only; never shown in release, so these labels are not part of the content packs.
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("DEBUG: no model. Label this photo") },
        text = {
            androidx.compose.foundation.layout.Column {
                LeafClass.entries.forEach { cls -> TextButton(onClick = { onPick(cls) }) { Text(cls.id) } }
            }
        },
        confirmButton = { TextButton(onClick = onSkip) { Text("unlabelled") } },
    )
}
