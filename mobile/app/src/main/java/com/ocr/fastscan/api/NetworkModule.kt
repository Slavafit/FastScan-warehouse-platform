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
    fun createService(context: Context, includeAuth: Boolean = true): OnzaApiService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = normalizeBaseUrl(prefs.getString("api_url", "https://onza-api.ibm.kg"))
        val tokenManager = TokenManager(context)

        // Добавляет Bearer токен в каждый запрос
        val authInterceptor = Interceptor { chain ->
            var request = chain.request()
            tokenManager.getToken()?.let { request = request.newBuilder().addHeader("Authorization", "Bearer $it").build() }
            var response = chain.proceed(request)
            if (response.code == 401) {  // Обработка 401
                runBlocking {  // FIX: runBlocking для suspend refreshToken
                    if (tokenManager.refreshToken()) {
                        response.close()
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

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (includeAuth) {
            clientBuilder.addInterceptor(authInterceptor)
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OnzaApiService::class.java)
    }

    private fun normalizeBaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty().ifBlank { "https://onza-api.ibm.kg" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
