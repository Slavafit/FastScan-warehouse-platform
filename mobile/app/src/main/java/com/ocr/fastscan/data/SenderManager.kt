package com.ocr.fastscan.data

import android.util.Log
import com.ocr.fastscan.api.OnzaApiService
import com.ocr.fastscan.data.model.SaveOrderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SenderManager(
    private val apiService: OnzaApiService
) {

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun submitOrder(
        request: SaveOrderRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        ioScope.launch {
            try {
                val response = apiService.saveOrder(request)
                if (response.isSuccessful) {
                    uiScope.launch { onSuccess() }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Нет тела ошибки"
                    uiScope.launch {
                        onError("Ошибка API: ${response.code()} - $errorBody")
                    }
                }
            } catch (e: Exception) {
                Log.e("SenderManager", "Исключение при отправке: ${e.message}", e)
                uiScope.launch {
                    onError("Ошибка сети: ${e.message}")
                }
            }
        }
    }

}
