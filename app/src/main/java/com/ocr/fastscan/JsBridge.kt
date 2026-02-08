package com.ocr.fastscan

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView

class JsBridge(
    private val ctx: Context,
    private val web: WebView
) {

    companion object {
        var lastType = ""
        var lastValue = ""
    }

    @JavascriptInterface
    fun applyResult() {
        val type = lastType
        val value = lastValue

        val js = when (type) {

            "barcode" -> """
                let f = document.querySelector('input[placeholder="Поиск по треку..."]');
                if(f){
                    f.value = "$value";
                    f.dispatchEvent(new Event('input'));
                    f.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'}));
                    f.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter'}));
                }
            """

            "kg" -> """
                let f = document.getElementById("Код клиента");
                if(f){
                    f.value = "$value";
                    f.dispatchEvent(new Event('input'));
                    f.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'}));
                    f.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter'}));
                }
            """

            "weight" -> """
                let f = document.getElementById("weight");
                if(f){
                    f.value = "$value";
                    f.dispatchEvent(new Event('input'));
                    f.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'}));
                    f.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter'}));
                }
            """

            else -> ""
        }

        web.post {
            web.evaluateJavascript(js, null)
        }
    }
}
