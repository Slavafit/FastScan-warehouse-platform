package com.ocr.fastscan.api

import android.content.Context
import com.ocr.fastscan.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.runBlocking


object NetworkModule {
    fun createService(context: Context): OnzaApiService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = prefs.getString("api_url", "https://onza.ibm.kg/api/")
        val tokenManager = TokenManager(context)

        // Добавляет Bearer токен в каждый запрос
        val authInterceptor = Interceptor { chain ->
            var request = chain.request()
            tokenManager.getToken()?.let { request = request.newBuilder().addHeader("Authorization", "Bearer $it").build() }
            var response = chain.proceed(request)
            if (response.code == 401) {  // Обработка 401
                runBlocking {  // FIX: runBlocking для suspend refreshToken
                    if (tokenManager.refreshToken()) {
                        request = request.newBuilder().addHeader("Authorization", "Bearer ${tokenManager.getToken()}").build()
                        response = chain.proceed(request)
                    }
                }
            }
            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OnzaApiService::class.java)
    }
}