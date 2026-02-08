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

        setupWebView()
        setupButtons()
    }

    // Этот метод срабатывает, когда мы возвращаемся из Камеры
    override fun onResume() {
        super.onResume()
        if (JsBridge.lastValue.isNotEmpty()) {
            jsBridge.applyResult()
            // Очищаем, чтобы не вставлять дважды
            JsBridge.lastType = ""
            JsBridge.lastValue = ""
        }
    }

    private fun setupWebView() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val siteUrl = prefs.getString("site_url", "https://onza.ibm.kg/")
        val login = prefs.getString("login", "")
        val password = prefs.getString("password", "")

        jsBridge = JsBridge(this, binding.webView)

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.addJavascriptInterface(jsBridge, "ANDROID")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Авто-логин
                if (!login.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    val js = """
                        try {
                           document.getElementsByName('login')[0].value = '$login';
                           document.getElementsByName('password')[0].value = '$password';
                           document.querySelector('button[type="submit"]').click(); 
                        } catch(e){}
                    """
                    view.evaluateJavascript(js, null)
                }
            }
        }

        if (!siteUrl.isNullOrEmpty()) {
            binding.webView.loadUrl(siteUrl!!)
        }
    }

    private fun setupButtons() {
        binding.fabBarcode.setOnClickListener {
            openCamera("barcode")
        }

        binding.fabKg.setOnClickListener {
            openCamera("kg")
        }

        binding.fabWeight.setOnClickListener {
            openCamera("weight")
        }

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