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
        Log.d("TokenManager", "Токены сохранены. Access: ${accessToken.take(15)}...")
    }

    fun getToken(): String? = prefs.getString("bearer_token", null)

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() {
        prefs.edit().clear().apply()
        Log.d("TokenManager", "Все токены очищены")
    }

    // Исправленный метод обновления токена
    suspend fun refreshToken(): Boolean {
        val refresh = getRefreshToken() ?: return false
        Log.d("TokenManager", "Попытка обновления токена...")
        return try {
            val apiService = NetworkModule.createService(appContext, includeAuth = false)
            val res = apiService.refresh(mapOf("refresh_token" to refresh))

            if (res.isSuccessful && res.body()?.accessToken != null) {
                val body = res.body()!!
                saveTokens(body.accessToken!!, body.refreshToken ?: refresh)
                Log.d("TokenManager", "Токен успешно обновлён")
                true
            } else {
                Log.e("TokenManager", "Неуспешный refresh: ${res.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Ошибка при refreshToken", e)
            false
        }
    }
}
