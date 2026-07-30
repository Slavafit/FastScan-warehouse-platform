package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.ocr.fastscan.data.model.WeightResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object WeightServerHelper {

    private val client = OkHttpClient()

    /**
     * Получает вес с сервера
     * @return вес в формате "0.49" или null в случае ошибки
     */
    suspend fun getWeight(context: Context): String? {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = prefs.getString("weight_server_url", "http://192.168.1.50")?.trim()
            ?: "http://192.168.1.50"

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return try {
            val response = withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()
                ).execute()
            }

            if (response.isSuccessful) {
                val json = response.body?.string() ?: ""
                //Log.d("WeightServer", "Ответ от сервера: $json")

                val weightResponse = Gson().fromJson(json, WeightResponse::class.java)
                String.format("%.2f", weightResponse.weight)
            } else {
                Log.e("WeightServer", "Ошибка сервера: код ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("WeightServer", "Ошибка при запросе веса", e)
            null
        }
    }
}