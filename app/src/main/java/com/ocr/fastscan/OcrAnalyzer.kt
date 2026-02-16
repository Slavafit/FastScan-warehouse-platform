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

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                Log.d("с", "Распознанный текст: $rawText")  // Посмотри в Logcat, что видит камера
                // Логика поиска зависит от режима, который передала CameraActivity
                when (mode) {
                    "kg" -> {
                        // Ищем KG и цифры (KG123456)
                        // (?i) означает игнорирование регистра (kg, KG, Kg)
                        val pattern = Pattern.compile("KG\\s?(\\d{4,6})", Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(rawText)
                        if (matcher.find()) {
                            // Возвращаем полный код (KG + цифры), убрав пробелы и сделав CAPS
                            val result = matcher.group(0)?.replace(" ", "")?.uppercase() ?: ""
                            onResult(result)
                        }
                    }
                    "weight" -> {
                        // Ищем вес: цифры, точка, цифры (например, 0.65 или 12.5)
                        val pattern = Pattern.compile("\\d+\\.\\d+")
                        val matcher = pattern.matcher(rawText)
                        if (matcher.find()) {
                            val result = matcher.group()
                            onResult(result)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR Analyzer", "Ошибка распознавания", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}