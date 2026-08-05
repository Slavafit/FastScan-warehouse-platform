package com.ocr.fastscan

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

class OcrAnalyzer(
    private val mode: String,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val resultStabilizer = ScanResultStabilizer()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (mode == "weight") {
            // Weight now comes from the server, so no local action is needed here
            imageProxy.close()
            return
        }

        // KG mode scans the client code
        if (mode == "kg") {
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text.uppercase()
                    val pattern = Pattern.compile("KG\\s*(\\d{4,6})")
                    val matcher = pattern.matcher(rawText)
                    if (matcher.find()) {
                        resultStabilizer.add("KG" + matcher.group(1))?.let(onResult)
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}
