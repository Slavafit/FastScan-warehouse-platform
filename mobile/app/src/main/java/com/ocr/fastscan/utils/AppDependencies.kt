package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.ocr.fastscan.R
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.api.OnzaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppDependencies {
    lateinit var apiService: OnzaApiService
    lateinit var tokenManager: TokenManager
    lateinit var soundManager: SoundManager
    lateinit var printHelper: PrintHelper
    lateinit var firebaseManager: FirebaseManager

    fun init(context: Context) {
        apiService = NetworkModule.createService(context)
        tokenManager = TokenManager(context)
        soundManager = SoundManager(context)
        firebaseManager = FirebaseManager()
    }

    suspend fun performAutoLogin(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val loginVal = prefs.getString("login", "")?.trim().orEmpty()
        val passVal = prefs.getString("password", "")?.trim().orEmpty()

        if (loginVal.isEmpty() || passVal.isEmpty()) {
            Toast.makeText(context, R.string.login_not_filled, Toast.LENGTH_LONG).show()
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val loginService = NetworkModule.createService(appContext, includeAuth = false)
                val response = loginService.login(mapOf("login" to loginVal, "password" to passVal))

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val authData = response.body()
                        val accessToken = authData?.accessToken

                        if (!accessToken.isNullOrEmpty()) {
                            tokenManager.saveTokens(accessToken, authData.refreshToken.orEmpty())
                            apiService = NetworkModule.createService(appContext)
                            Toast.makeText(context, R.string.login_succes, Toast.LENGTH_SHORT).show()
                            Log.d("Auth", "Auto-login successful. Token saved.")
                            true
                        } else {
                            Log.e("Auth", "Login response has no access token: ${response.code()}")
                            Toast.makeText(context, R.string.token_error, Toast.LENGTH_SHORT).show()
                            false
                        }
                    } else {
                        Log.e("Auth", "Login failed: ${response.code()}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.net_error_code, response.code()),
                            Toast.LENGTH_SHORT
                        ).show()
                        false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("Auth", "Auto-login error", e)
                    Toast.makeText(context, R.string.net_error_short, Toast.LENGTH_LONG).show()
                    false
                }
            }
        }
    }
}
