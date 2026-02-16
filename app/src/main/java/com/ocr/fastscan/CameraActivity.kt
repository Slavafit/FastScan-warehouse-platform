package com.ocr.fastscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ocr.fastscan.databinding.ActivityCameraBinding
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var mode = "barcode" // barcode, kg, weight
    private var isProcessing = false // Блокировка от двойных срабатываний

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 1. Получаем режим из Intent
        mode = intent.getStringExtra("scan_mode") ?: "barcode"
        Log.d("DEBUG_SCAN", "CameraActivity запущен в режиме: $mode")

        // 2. Настраиваем подсказку
        binding.tvHint.text = when(mode) {
            "kg" -> "Наведите на код клиента (KG...)"
            "weight" -> "Наведите на цифры веса (0.00)"
            else -> "Наведите на штрих-код"
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Настройщик анализатора
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // ГЛАВНОЕ ИСПРАВЛЕНИЕ:
            // Выбираем, какой анализатор подключить, в зависимости от режима
            val myAnalyzer: ImageAnalysis.Analyzer = if (mode == "barcode") {
                // Если режим штрихкода
                BarcodeAnalyzer { result ->
                    returnResult(result)
                }
            } else {
                // Если режим "kg" или "weight" — используем OCR
                // Мы передаем 'mode' внутрь, чтобы OcrAnalyzer знал, какой Regex применить
                OcrAnalyzer(mode) { result ->
                    returnResult(result)
                }
            }

            imageAnalysis.setAnalyzer(cameraExecutor, myAnalyzer)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("Camera", "Ошибка запуска камеры", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun returnResult(value: String) {
        if (isProcessing) return
        isProcessing = true

        val resultIntent = Intent()
        resultIntent.putExtra("scan_result", value)
        resultIntent.putExtra("scan_mode", mode) // Возвращаем режим обратно, чтобы MainActivity знала, куда вставить текст
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}