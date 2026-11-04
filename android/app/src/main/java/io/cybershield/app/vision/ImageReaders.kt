package io.cybershield.app.vision

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device image readers. Images are processed by bundled ML Kit models and never uploaded.
 * Only images the user explicitly picks (photo picker) or shares to CyberShield are read.
 */
object ImageReaders {
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private suspend fun load(context: Context, uri: Uri): InputImage = withContext(Dispatchers.IO) {
        InputImage.fromFilePath(context, uri)
    }

    /** OCR with the Devanagari model, which also recognises Latin script (English, Hinglish). */
    suspend fun readText(context: Context, uri: Uri): String {
        val image = logFailure("load image") { load(context, uri) }
        val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        return try {
            logFailure("recognise text") { recognizer.process(image).await().text }
        } finally {
            recognizer.close()
        }
    }

    /** Logs only the failing step and exception type, never image content or recognised text. */
    private inline fun <T> logFailure(step: String, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        android.util.Log.w("CyberShield", "Image reader failed to $step: ${e.javaClass.simpleName}")
        throw e
    }

    /** Decodes the first QR code in a picture, e.g. a QR screenshot received on WhatsApp. */
    suspend fun readQr(context: Context, uri: Uri): String? {
        val image = load(context, uri)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        return try {
            scanner.process(image).await().firstNotNullOfOrNull { it.rawValue }
        } finally {
            scanner.close()
        }
    }
}
