package io.cybershield.app.ml

import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * On-device scam classifier: an exact Kotlin port of the trained baseline in `ml/`.
 *
 * It reproduces the Python pipeline bit-for-bit — the same md5 feature hashing, character/word n-grams,
 * logistic regression and temperature calibration — which `MlClassifierParityTest` verifies against a
 * reference exported alongside the model. Loaded from the bundled asset `ml/scam_classifier.json`.
 *
 * [scamProbability] returns a value only when the raw probability clears [activationThreshold], so the
 * model (which, through [io.cybershield.app.security.MlFusion], can only raise risk) fires only when it
 * is confident. The threshold is chosen during export to keep false positives at zero on the dev set.
 */
class LogisticTextClassifier internal constructor(
    private val charNgrams: IntArray,
    private val wordNgrams: IntArray,
    private val dim: Int,
    private val lowercase: Boolean,
    private val weights: DoubleArray,
    private val bias: Double,
    private val temperature: Double,
    private val activationThreshold: Double,
) : MlClassifier {

    override val isAvailable: Boolean = true

    /** Raw, ungated calibrated probability. Exposed for parity testing; callers use [scamProbability]. */
    fun rawProbability(text: String): Double {
        val feats = transform(text)
        var z = bias
        for ((idx, value) in feats) z += weights[idx] * value
        return sigmoid(z / temperature)
    }

    override fun scamProbability(text: String): Double? {
        val p = rawProbability(text)
        return if (p >= activationThreshold) p else null
    }

    private fun transform(input: String): Map<Int, Double> {
        val text = if (lowercase) input.lowercase() else input
        val counts = HashMap<Int, Double>()
        val padded = " $text "
        for (n in charNgrams) {
            if (n <= 0 || padded.length < n) continue
            for (i in 0..padded.length - n) add(counts, "c$n:" + padded.substring(i, i + n))
        }
        val words = Regex("\\S+").findAll(text).map { it.value }.toList()
        for (n in wordNgrams) {
            if (n <= 0 || words.size < n) continue
            for (i in 0..words.size - n) add(counts, "w$n:" + words.subList(i, i + n).joinToString(" "))
        }
        var norm = 0.0
        for (v in counts.values) norm += v * v
        norm = sqrt(norm).let { if (it == 0.0) 1.0 else it }
        val out = HashMap<Int, Double>(counts.size)
        for ((k, v) in counts) out[k] = v / norm
        return out
    }

    private fun add(counts: HashMap<Int, Double>, token: String) {
        val bucket = hashBucket(token)
        counts[bucket] = (counts[bucket] ?: 0.0) + 1.0
    }

    private fun hashBucket(token: String): Int {
        val digest = MessageDigest.getInstance("MD5").digest(token.toByteArray(Charsets.UTF_8))
        val v = ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
            ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
        return (v % dim).toInt()
    }

    companion object {
        private fun sigmoid(z: Double): Double =
            if (z >= 0) 1.0 / (1.0 + Math.exp(-z)) else Math.exp(z).let { it / (1.0 + it) }

        /** Parses the exported asset. Throws if the JSON is missing required fields. */
        fun fromJson(json: String): LogisticTextClassifier {
            val o = JSONObject(json)
            val f = o.getJSONObject("featurizer")
            fun ints(key: String): IntArray {
                val a = f.getJSONArray(key)
                return IntArray(a.length()) { a.getInt(it) }
            }
            val dim = o.getInt("dim")
            val weights = DoubleArray(dim)
            val w = o.getJSONObject("weights")
            val keys = w.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                weights[k.toInt()] = w.getDouble(k)
            }
            return LogisticTextClassifier(
                charNgrams = ints("char_ngrams"),
                wordNgrams = ints("word_ngrams"),
                dim = dim,
                lowercase = f.optBoolean("lowercase", true),
                weights = weights,
                bias = o.getDouble("bias"),
                temperature = o.getDouble("temperature"),
                activationThreshold = o.getDouble("activation_threshold"),
            )
        }
    }
}
