//BarcodeAnalyzer.kt
package com.ocr.fastscan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private val resultStabilizer = ScanResultStabilizer()

    override fun analyze(p0: ImageProxy) {

        val media = p0.image ?: run {
            p0.close()
            return
        }

        val input = InputImage.fromMediaImage(media, p0.imageInfo.rotationDegrees)

        scanner.process(input)
            .addOnSuccessListener { list ->
                if (list.isNotEmpty()) {
                    val code = list[0].rawValue ?: ""
                    resultStabilizer.add(code)?.let(onResult)
                }
            }
            .addOnCompleteListener {
                p0.close()
            }
    }
}

