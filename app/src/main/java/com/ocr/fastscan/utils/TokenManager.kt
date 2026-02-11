package com.ocr.fastscan.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ocr.fastscan.api.NetworkModule

class TokenManager(private val appContext: Context) {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

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

    suspend fun refreshToken(): Boolean {
        val refresh = getRefreshToken() ?: return false
        val apiService = NetworkModule.createService(appContext)  // Создаём временный service
        try {
            val res = apiService.refresh(mapOf("refresh_token" to refresh))  // Предполагаю метод в OnzaApiService
            if (res.isSuccessful && res.body()?.bearer != null) {
                saveTokens(res.body()!!.bearer!!, res.body()!!.refreshBearer ?: "")
                return true
            }
        } catch (e: Exception) {
            Log.e("RefreshError", "Refresh failed: ${e.message}")
        }
        return false
    }
}