package com.ocr.fastscan

import android.webkit.JavascriptInterface
import android.webkit.WebView

class JsBridge(private val webView: WebView) {

    companion object {
        var lastType: String = ""
        var lastValue: String = ""
    }

    @JavascriptInterface
    fun applyResult() {
        val type = lastType
        val value = lastValue
        if (value.isEmpty()) return

        val js = """
            (function() {
                function triggerEvents(el) {
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
                }

                let field;
                if ("$type" === "barcode") {
                    field = document.querySelector('input[placeholder*="Поиск"], input[placeholder*="трек"]');
                } else if ("$type" === "kg") {
                    field = document.getElementById('Код клиента') || document.querySelector('input[name*="client"]');
                } else if ("$type" === "weight") {
                    field = document.getElementById('weight') || document.querySelector('input[name*="weight"]');
                }

                if (field) {
                    field.value = "$value";
                    triggerEvents(field);
                    
                    // Если это был вес, возвращаем фокус на поле поиска через 1 сек
                    if ("$type" === "weight") {
                        setTimeout(() => {
                            let searchField = document.querySelector('input[placeholder*="Поиск"]');
                            if (searchField) searchField.focus();
                        }, 1000);
                    }
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }
}