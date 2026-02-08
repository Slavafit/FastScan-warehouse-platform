package com.ocr.fastscan

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.ocr.fastscan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var jsBridge: JsBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsBridge = JsBridge(binding.webView)
        setupWebView()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // Когда вернулись из камеры — применяем результат
        if (JsBridge.lastValue.isNotEmpty()) {
            jsBridge.applyResult()
            JsBridge.lastValue = "" // Очищаем после вставки
        }
    }

    private fun setupWebView() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val siteUrl = prefs.getString("site_url", "https://onza.ibm.kg/")
        val login = prefs.getString("login", "")
        val pass = prefs.getString("password", "")

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        binding.webView.addJavascriptInterface(jsBridge, "ANDROID")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Авто-логин при загрузке страницы входа
                if (!login.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                    val authJs = """
                        let userF = document.querySelector('input[name*="login"], input[name*="username"]');
                        let passF = document.querySelector('input[type="password"]');
                        let btn = document.querySelector('button[type="submit"], input[type="button"][value*="Войти"]');
                        if (userF && passF) {
                            userF.value = '$login';
                            passF.value = '$pass';
                            if (btn) btn.click();
                        }
                    """.trimIndent()
                    view.evaluateJavascript(authJs, null)
                }
            }
        }
        binding.webView.loadUrl(siteUrl!!)
    }

    private fun setupButtons() {
        binding.fabBarcode.setOnClickListener { openCamera("barcode") }
        binding.fabKg.setOnClickListener { openCamera("kg") }
        binding.fabWeight.setOnClickListener { openCamera("weight") }
        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun openCamera(mode: String) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("mode", mode)
        startActivity(intent)
    }
}