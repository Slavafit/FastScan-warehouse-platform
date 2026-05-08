package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.ocr.fastscan.R
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.api.OnzaApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
        firebaseManager = FirebaseManager()
    }

    // Вызов этой функции из Activity или Fragment
    // ====================== АВТО-ЛОГИН ======================
    fun performAutoLogin(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val loginVal = prefs.getString("login", "")?.trim() ?: ""
        val passVal = prefs.getString("password", "")?.trim() ?: ""

        if (loginVal.isEmpty() || passVal.isEmpty()) {
            Toast.makeText(context, R.string.login_not_filled, Toast.LENGTH_LONG).show()
            return
        }

        // Запускаем корутину
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.login(mapOf("login" to loginVal, "password" to passVal))

                withContext(Dispatchers.Main) {   // Переключаемся обратно на главный поток для UI
                    if (response.isSuccessful) {
                        val authData = response.body()
                        if (authData?.accessToken != null) {
                            tokenManager.saveTokens(
                                authData.accessToken,
                                authData.refreshToken ?: ""
                            )
                            Toast.makeText(context,
                                (R.string.login_succes),
                                Toast.LENGTH_SHORT)
                                .show()
                            //Log.d("Auth", "Авто-логин успешен. Токен сохранён.")
                        } else {
                            Toast.makeText(context, R.string.token_error, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context,
                            context.getString(
                                R.string.net_error,
                                response.code()),
                            Toast.LENGTH_SHORT).show()
                        Log.e("Auth", "Неуспешный ответ сервера: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("Auth", "Ошибка при авто-логине", e)
                    Toast.makeText(context, R.string.net_error_short, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}