package io.cybershield.app.ml

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the Kotlin on-device classifier reproduces the Python-trained baseline exactly, the same way
 * WebReferenceTest pins the JS engine to Kotlin. The reference (text -> probability) is exported next to
 * the model by `ml/ (python -m cybershield_ml.cli export)`. If this fails after retraining, re-run export
 * so the bundled asset and the reference are regenerated together.
 */
class MlClassifierParityTest {
    private val assetFile = File("src/main/assets/ml/scam_classifier.json")

    private fun reference(): JSONObject =
        JSONObject(javaClass.classLoader!!.getResourceAsStream("ml/parity.json")!!.bufferedReader().readText())

    @Test fun bundledModelLoads() {
        assertTrue("missing bundled model asset: ${assetFile.absolutePath}", assetFile.exists())
        val clf = LogisticTextClassifier.fromJson(assetFile.readText())
        assertTrue(clf.isAvailable)
    }

    @Test fun kotlinProbabilitiesMatchPythonReference() {
        val clf = LogisticTextClassifier.fromJson(assetFile.readText())
        val cases = reference().getJSONArray("cases")
        assertTrue("no parity cases", cases.length() > 0)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val text = c.getString("text")
            val expected = c.getDouble("prob")
            assertEquals("parity mismatch for: ${text.take(60)}", expected, clf.rawProbability(text), 1e-4)
        }
    }

    @Test fun onlyFiresAboveActivationThreshold() {
        val clf = LogisticTextClassifier.fromJson(assetFile.readText())
        val threshold = reference().getDouble("activation_threshold")
        val cases = reference().getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val text = cases.getJSONObject(i).getString("text")
            val raw = clf.rawProbability(text)
            val gated = clf.scamProbability(text)
            if (raw >= threshold) assertEquals(raw, gated!!, 1e-9) else assertNull(gated)
        }
    }
}
