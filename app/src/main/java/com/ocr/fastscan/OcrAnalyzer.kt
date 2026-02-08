package com.ocr.fastscan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
// ВАЖНО: Добавился этот импорт
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

class OcrAnalyzer(
    private val mode: String,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // ИСПРАВЛЕНИЕ: Передаем DEFAULT_OPTIONS внутрь getClient(...)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(p0: ImageProxy) {
        val media = p0.image ?: run {
            p0.close()
            return
        }

        val input = InputImage.fromMediaImage(media, p0.imageInfo.rotationDegrees)

        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.replace("\n", " ").trim()

                when (mode) {
                    "kg" -> {
                        // Ищем паттерн KG + цифры
                        val p = Pattern.compile("KG(\\d{4,6})", Pattern.CASE_INSENSITIVE)
                        val m = p.matcher(raw)
                        if (m.find()) {
                            onResult(m.group(1))
                        }
                    }

                    "weight" -> {
                        // Ищем вес (число с точкой), например 12.50
                        val p = Pattern.compile("\\d+\\.\\d+")
                        val m = p.matcher(raw)
                        if (m.find()) {
                            onResult(m.group())
                        }
                    }
                }
            }
            .addOnFailureListener {
                // Ошибки игнорируем, просто продолжаем сканировать
            }
            .addOnCompleteListener {
                p0.close()
            }
    }
}