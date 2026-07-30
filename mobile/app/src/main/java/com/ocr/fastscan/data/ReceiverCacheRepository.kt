package com.ocr.fastscan.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ocr.fastscan.api.OnzaApiService
import com.ocr.fastscan.data.model.ReceiverItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReceiverCacheRepository(
    context: Context,
    private val apiService: OnzaApiService,
    private val gson: Gson = Gson()
) {
    private val cacheFile = File(context.applicationContext.cacheDir, CACHE_FILE_NAME)
    private var memoryCache: List<ReceiverItem>? = null

    suspend fun getReceivers(): List<ReceiverItem> = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }

        readCache(requireFresh = true)?.let {
            memoryCache = it
            return@withContext it
        }

        try {
            val response = apiService.getAllReceivers()
            if (!response.isSuccessful) throw IllegalStateException("Server error ${response.code()}")

            val receivers = response.body() ?: emptyList()
            memoryCache = receivers
            writeCache(receivers)
            receivers
        } catch (e: Exception) {
            readCache(requireFresh = false)?.let {
                memoryCache = it
                return@withContext it
            }
            throw e
        }
    }

    private fun readCache(requireFresh: Boolean): List<ReceiverItem>? {
        if (!cacheFile.exists()) return null
        if (requireFresh && System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) return null

        return runCatching {
            val type = object : TypeToken<List<ReceiverItem>>() {}.type
            gson.fromJson<List<ReceiverItem>>(cacheFile.readText(), type)
        }.getOrNull()
    }

    private fun writeCache(receivers: List<ReceiverItem>) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(gson.toJson(receivers))
        }
    }

    private companion object {
        const val CACHE_FILE_NAME = "receivers_cache.json"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
