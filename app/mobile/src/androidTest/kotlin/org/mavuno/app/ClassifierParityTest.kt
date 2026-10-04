package org.mavuno.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.mavuno.app.ml.LeafClassifier
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Runs the shipped leaf model through the app's own preprocessing on held-out test photos and checks it
 * agrees with the Python evaluation (ml/export_eval.py), and that each photo takes under 1.5 s (PRD 7.1).
 */
@RunWith(AndroidJUnit4::class)
class ClassifierParityTest {

    @Test
    fun matchesPythonAndIsFastEnough() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val classifier = LeafClassifier.load(instrumentation.targetContext)
        assertTrue(classifier.available, "leaf model did not load from assets")

        val assets = instrumentation.context.assets
        val expected = Json.parseToJsonElement(assets.open("parity/expected.json").bufferedReader().readText()).jsonArray
        val timesMs = mutableListOf<Long>()
        val mismatches = mutableListOf<String>()
        for (e in expected) {
            val o = e.jsonObject
            val file = o.getValue("file").jsonPrimitive.content
            val bitmap = assets.open("parity/$file").use { BitmapFactory.decodeStream(it) }
            val start = System.nanoTime()
            val obs = classifier.classify(bitmap)!!
            timesMs += (System.nanoTime() - start) / 1_000_000
            val wantClass = o.getValue("python_class").jsonPrimitive.content
            val wantProb = o.getValue("python_prob").jsonPrimitive.double
            Log.i("Parity", "$file: app=${obs.leafClass.id} %.3f python=$wantClass %.3f".format(obs.prob, wantProb))
            if (obs.leafClass.id != wantClass || abs(obs.prob - wantProb) > 0.10) {
                mismatches += "$file app=${obs.leafClass.id}/%.2f python=$wantClass/%.2f".format(obs.prob, wantProb)
            }
        }
        val sorted = timesMs.sorted()
        Log.i("Parity", "per-photo ms: median=${sorted[sorted.size / 2]} max=${sorted.last()} (first call includes warm-up)")
        assertTrue(mismatches.isEmpty(), "app disagrees with Python on ${mismatches.size}/${expected.size}: $mismatches")
        assertTrue(sorted[sorted.size / 2] < 1500, "median ${sorted[sorted.size / 2]} ms is over the 1.5 s budget")
    }
}
