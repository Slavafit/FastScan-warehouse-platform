package com.ocr.fastscan.utils

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveTokens(bearer: String, refresh: String) {
        prefs.edit().apply {
            putString("bearer_token", bearer)
            putString("refresh_token", refresh)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString("bearer_token", null)

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() = prefs.edit().clear().apply()
}