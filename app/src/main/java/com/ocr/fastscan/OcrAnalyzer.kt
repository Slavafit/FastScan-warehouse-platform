package com.ocr.fastscan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

class OcrAnalyzer(
    private val mode: String,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (mode == "weight") {
            // Специально для весов — преобразуем в bitmap и ищем ярко-красные пиксели
            val bitmap = imageProxy.toBitmap() ?: run { imageProxy.close(); return }
            val weight = detectWeightFromRedDisplay(bitmap)
            if (weight.isNotEmpty()) {
                Log.d("WEIGHT_7SEG", "Распознан вес: $weight")
                onResult(weight)
            }
            imageProxy.close()
            return
        }

        // Для KG-кода — обычный ML Kit Text
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.uppercase()
                Log.d("OCR_RAW", rawText)
                if (mode == "kg") {
                    val pattern = Pattern.compile("KG\\s*(\\d{4,6})")
                    val matcher = pattern.matcher(rawText)
                    if (matcher.find()) {
                        val result = "KG" + matcher.group(1)
                        onResult(result.uppercase())
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    // 7-сегментное распознавание для красных весов
    private fun detectWeightFromRedDisplay(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        var redPixels = 0
        var totalPixels = 0
        // Считаем ярко-красные пиксели (R > 150, G < 80, B < 80) — типично для LED
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r > 140 && g < 100 && b < 100) {
                    redPixels++
                }
                totalPixels++
            }
        }
        if (redPixels < 500) return "" // слишком мало красного — не дисплей весов
        // Делим изображение на зоны цифр (примерно 7 зон по ширине)
        val cropHeight = height * 0.3f..height * 0.8f // обрезаем верх/низ
        val digitWidth = width / 7.5f
        val digits = mutableListOf<String>()
        for (i in 0..6) {
            val left = (i * digitWidth).toInt()
            val right = ((i + 1) * digitWidth).toInt().coerceAtMost(width)
            val segmentBrightness = getSegmentBrightness(bitmap, left, right, cropHeight)
            digits.add(classifyDigit(segmentBrightness))
        }
        val result = digits.joinToString("").replace(" ", "")
        // Ищем паттерн вроде "0.663" или " 0.663 "
        val clean = result.replace(Regex("[^0-9.]"), "")
        return if (clean.contains(".")) clean else ""
    }
    private fun getSegmentBrightness(bitmap: Bitmap, left: Int, right: Int, yRange: ClosedFloatingPointRange<Float>): Double {
        var bright = 0
        var count = 0
        for (x in left until right) {
            for (y in yRange.start.toInt() until yRange.endInclusive.toInt().coerceAtMost(bitmap.height)) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 140 && Color.green(pixel) < 100) {
                    bright += Color.red(pixel)
                    count++
                }
            }
        }
        return if (count > 0) bright.toDouble() / count else 0.0
    }
    private fun classifyDigit(brightness: Double): String {
        return when {
            brightness < 50 -> " "
            brightness < 200 -> "1"
            brightness < 350 -> "7"
            brightness < 500 -> "4"
            brightness < 650 -> "2"
            brightness < 800 -> "5"
            brightness < 950 -> "0"
            brightness < 1100 -> "6"
            brightness < 1300 -> "3"
            brightness < 1500 -> "9"
            else -> "8"
        }
    }
}

// Вспомогательная функция для ImageProxy → Bitmap
fun ImageProxy.toBitmap(): Bitmap? {
    if (format != ImageFormat.YUV_420_888) return null

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()

    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}