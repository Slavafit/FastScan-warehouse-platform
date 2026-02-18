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

        // Предобработка: усиливаем красный канал, уменьшаем шум
        val processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r > 100 && g < 80 && b < 80) {
                    processedBitmap.setPixel(x, y, Color.rgb(r * 2, g / 2, b / 2)) // Усиливаем красный
                } else {
                    processedBitmap.setPixel(x, y, Color.BLACK) // Фон чёрный
                }
            }
        }

        var redPixels = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = processedBitmap.getPixel(x, y)
                if (Color.red(pixel) > 140) redPixels++
            }
        }
        if (redPixels < 400) return "" // Слишком мало красного — не дисплей

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
        val result = digits.joinToString("")
        Log.d("WEIGHT_7SEG_RAW", "Распознанные сегменты: $result")
        // Очистка: ищем паттерн 0.XXX или X.XXX
        val clean = result.replace(Regex("[^0-9.]"), "").trim()
        val matcher = Pattern.compile("(\\d{1,2}\\.\\d{1,3})").matcher(clean)
        return if (matcher.find()) matcher.group(1)!! else ""
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
            brightness < 40 -> " "
            brightness < 150 -> "1"
            brightness < 280 -> "7"
            brightness < 400 -> "4"
            brightness < 520 -> "2"
            brightness < 640 -> "5"
            brightness < 760 -> "0"
            brightness < 880 -> "6"
            brightness < 1000 -> "3"
            brightness < 1200 -> "9"
            brightness < 1400 -> "8"
            brightness < 1600 -> "."  // Точка имеет меньше сегментов
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