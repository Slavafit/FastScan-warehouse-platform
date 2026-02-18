package com.ocr.fastscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
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
    private var mode = "barcode"
    private var isProcessing = false
    private var cameraControl: CameraControl? = null
    private var currentExposureIndex = -6
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mode = intent.getStringExtra("scan_mode") ?: "barcode"

        Log.d("DEBUG_SCAN", "CameraActivity запущен в режиме: $mode")

        // Текст подсказки (убедись, что tvHint есть в XML)
        binding.tvHint?.text = when (mode) {
            "kg" -> "Наведите на код клиента (KG...)"
            "weight" -> "Наведите на дисплей весов"
            else -> "Наведите на штрих-код трека"
        }

        // Показываем нужные кнопки в зависимости от режима
        when (mode) {
            "weight" -> {
                binding.exposureControls.visibility = View.VISIBLE
                binding.btnFlashToggle.visibility = View.GONE
            }
            else -> {
                binding.exposureControls.visibility = View.GONE
                binding.btnFlashToggle.visibility = View.VISIBLE
                binding.btnFlashToggle.text = "Фонарик ВКЛ"
            }
        }
        // Обработчики кнопок
        binding.btnDarker.setOnClickListener { adjustExposure(-2) }
        binding.btnBrighter.setOnClickListener { adjustExposure(2) }

        binding.btnFlashToggle.setOnClickListener {
            isFlashOn = !isFlashOn
            setFlash(isFlashOn)
            binding.btnFlashToggle.text = if (isFlashOn) "Фонарик ВЫКЛ" else "Фонарик ВКЛ"
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

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer: ImageAnalysis.Analyzer = if (mode == "barcode") {
                BarcodeAnalyzer { result -> returnResult(result) }
            } else {
                OcrAnalyzer(mode) { result -> returnResult(result) }
            }

            imageAnalyzer.setAnalyzer(cameraExecutor, analyzer)

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )

                cameraControl = camera.cameraControl

            // Начальная экспозиция только для режима weight
                if (mode == "weight") {
                    val exposureState = camera.cameraInfo.exposureState
                    if (exposureState.isExposureCompensationSupported) {
                        cameraControl?.setExposureCompensationIndex(currentExposureIndex)
                        Log.d("CAMERA_EXPOSURE", "Экспозиция установлена: $currentExposureIndex")
                    }
                }
            } catch (exc: Exception) {
                Log.e("Camera", "Ошибка запуска камеры", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun adjustExposure(delta: Int) {
        currentExposureIndex += delta
        currentExposureIndex = currentExposureIndex.coerceIn(-20, 2)
        cameraControl?.setExposureCompensationIndex(currentExposureIndex)
        Log.d("CAMERA_EXPOSURE", "Новая экспозиция: $currentExposureIndex")
    }

    private fun setFlash(on: Boolean) {
        cameraControl?.enableTorch(on)
        Log.d("CAMERA_FLASH", "Фонарик: ${if (on) "ВКЛ" else "ВЫКЛ"}")
    }

    private fun returnResult(value: String) {
        if (isProcessing) return
        isProcessing = true

        Log.d("DEBUG_SCAN", "Результат найден ($mode): $value")

        val intent = Intent()
        intent.putExtra("scan_result", value)
        intent.putExtra("scan_mode", mode)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_SHORT).show()
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