package com.ocr.fastscan

import android.graphics.*
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

    // Маски для 7-сегментного дисплея: [a, b, c, d, e, f, g]
    private val digitMasks = mapOf(
        "1111110" to "0", "0110000" to "1", "1101101" to "2",
        "1111001" to "3", "0110011" to "4", "1011011" to "5",
        "1011111" to "6", "1110000" to "7", "1111111" to "8", "1111011" to "9"
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (mode == "weight") {
            // Режим ВЕСЫ: используем алгоритм по точкам
            val bitmap = imageProxy.toBitmap() ?: run { imageProxy.close(); return }
            val weight = detectWeightByPoints(bitmap)
            if (weight.isNotEmpty()) {
                Log.d("OCR_WEIGHT", "Распознан вес: $weight")
                onResult(weight)
            }
            imageProxy.close()
            return
        }

        // Режим KG: используем ML Kit
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.uppercase()
                if (mode == "kg") {
                    // Ищем KG и от 3 до 8 цифр (игнорируя пробелы)
                    val pattern = Pattern.compile("KG\\s*(\\d{3,8})")
                    val matcher = pattern.matcher(rawText)
                    if (matcher.find()) {
                        val result = "KG" + matcher.group(1)
                        onResult(result)
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun detectWeightByPoints(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Ищем границы табло (красные пиксели)
        var minX = width; var maxX = 0; var minY = height; var maxY = 0
        var found = false
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                if (isRedColor(bitmap.getPixel(x, y))) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                    found = true
                }
            }
        }
        if (!found || (maxX - minX) < 40) return ""

        // 2. Делим табло на 5 зон для цифр
        val displayWidth = maxX - minX
        val digitW = displayWidth / 5
        val sb = StringBuilder()

        for (i in 0 until 5) {
            val dLeft = minX + (i * digitW)
            sb.append(recognizeSingleDigit(bitmap, dLeft, minY, digitW, maxY - minY))
        }

        return sb.toString().trim().replace(Regex("^0+(?!\\.)"), "")
    }

    private fun recognizeSingleDigit(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): String {
        val midX = x + w / 2
        val midY = y + h / 2
        val seg = BooleanArray(7)

        try {
            // Точки с учетом наклона (как на фото)
            seg[0] = isRedColor(bitmap.getPixel(midX + (w * 0.1).toInt(), y + (h * 0.15).toInt())) // a
            seg[1] = isRedColor(bitmap.getPixel(x + (w * 0.85).toInt(), y + (h * 0.3).toInt()))   // b
            seg[2] = isRedColor(bitmap.getPixel(x + (w * 0.75).toInt(), y + (h * 0.75).toInt()))  // c
            seg[3] = isRedColor(bitmap.getPixel(midX - (w * 0.1).toInt(), y + (h * 0.85).toInt())) // d
            seg[4] = isRedColor(bitmap.getPixel(x + (w * 0.15).toInt(), y + (h * 0.75).toInt()))  // e
            seg[5] = isRedColor(bitmap.getPixel(x + (w * 0.25).toInt(), y + (h * 0.3).toInt()))   // f
            seg[6] = isRedColor(bitmap.getPixel(midX, midY))                                     // g
        } catch (e: Exception) { return "" }

        val mask = seg.joinToString("") { if (it) "1" else "0" }
        val digit = digitMasks[mask] ?: ""

        // Проверка десятичной точки
        val hasDot = checkPointForRed(bitmap, x + (w * 0.7).toInt(), y + (h * 0.8).toInt(), w / 3, h / 4)
        return if (hasDot && digit.isNotEmpty()) "$digit." else digit
    }

    private fun isRedColor(pixel: Int): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        // Распознаем красный и "белый засвет" в центре сегмента
        return (r > 170 && g < 150 && b < 150) || (r > 230 && g > 230 && b > 200)
    }

    private fun checkPointForRed(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        var count = 0
        for (px in x until (x + w).coerceAtMost(bitmap.width)) {
            for (py in y until (y + h).coerceAtMost(bitmap.height)) {
                if (isRedColor(bitmap.getPixel(px, py))) count++
            }
        }
        return count > 8
    }
}

// Extension для ImageProxy
fun ImageProxy.toBitmap(): Bitmap? {
    val mediaImage = this.image ?: return null
    val planes = mediaImage.planes
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
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}