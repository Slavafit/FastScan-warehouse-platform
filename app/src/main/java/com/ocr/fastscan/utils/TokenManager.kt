package com.ocr.fastscan.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ocr.fastscan.api.NetworkModule

class TokenManager(private val appContext: Context) {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Сохраняем токены в SharedPreferences
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString("bearer_token", accessToken)
            putString("refresh_token", refreshToken)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString("bearer_token", null)

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() = prefs.edit().clear().apply()

    // Исправленный метод обновления токена
    suspend fun refreshToken(): Boolean {
        val refresh = getRefreshToken() ?: return false
        val apiService = NetworkModule.createService(appContext)
        try {
            // Отправляем текущий refresh_token на сервер
            val res = apiService.refresh(mapOf("refresh_token" to refresh))

            // ПРОВЕРКА: используем accessToken и refreshToken из нового ответа
            if (res.isSuccessful && res.body()?.accessToken != null) {
                val body = res.body()!!
                saveTokens(body.accessToken!!, body.refreshToken ?: "")
                return true
            }
        } catch (e: Exception) {
            Log.e("RefreshError", "Refresh failed: ${e.message}")
        }
        return false
    }
}