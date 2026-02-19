package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.api.OnzaApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


object AppDependencies {
    lateinit var apiService: OnzaApiService
    lateinit var tokenManager: TokenManager
    lateinit var soundManager: SoundManager
    lateinit var printHelper: PrintHelper
    lateinit var firebaseManager: FirebaseManager

    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        apiService = NetworkModule.createService(context)
        tokenManager = TokenManager(context)
        soundManager = SoundManager(context)
        printHelper = PrintHelper(context)
        firebaseManager = FirebaseManager(context)
    }

    // Вызов этой функции из Activity или Fragment
    fun performAutoLogin(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val loginVal = prefs.getString("login", "") ?: ""
        val passVal = prefs.getString("password", "") ?: ""

        if (loginVal.isEmpty() || passVal.isEmpty()) {
            Toast.makeText(context, "Логин или пароль пустые", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                val response = apiService.login(mapOf("login" to loginVal, "password" to passVal))
                if (response.isSuccessful) {
                    val authData = response.body()
                    if (authData?.accessToken != null) {
                        tokenManager.saveTokens(
                            authData.accessToken,
                            authData.refreshToken ?: ""
                        )
                        // Показываем Toast в UI-потоке
                        (context as? android.app.Activity)?.runOnUiThread {
                            Toast.makeText(context, "Успешный вход!", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("Auth", "Успешный вход! Токен сохранен.")
                    }
                }
            } catch (e: Exception) {
                Log.e("Auth", "Ошибка сети при логине: ${e.message}", e)
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Ошибка входа: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}