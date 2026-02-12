package com.ocr.fastscan

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

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.replace("\n", " ")

                when (mode) {
                    "kg" -> {
                        // Ищем KG и вырезаем только цифры через группу (\\d+)
                        val pattern = Pattern.compile("KG(\\d{4,6})", Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(rawText)
                        if (matcher.find()) {
                            onResult(matcher.group(0)!!.uppercase())
                        }
                    }
                    "weight" -> {
                        // Ищем цифры с точкой (например 12.50)
                        val pattern = Pattern.compile("\\d+\\.\\d+")
                        val matcher = pattern.matcher(rawText)
                        if (matcher.find()) {
                            onResult(matcher.group())
                        }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}