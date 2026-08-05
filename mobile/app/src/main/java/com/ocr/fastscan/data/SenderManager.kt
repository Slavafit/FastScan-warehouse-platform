package com.ocr.fastscan.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ocr.fastscan.api.OnzaApiService
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.SaveOrderRequest
import com.ocr.fastscan.utils.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SenderManager(
    private val apiService: OnzaApiService,
    private val firebaseManager: FirebaseManager,
    private val context: Context  // for Toast and other UI operations
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
                    // Create object for Firestore
                    val shipment = FirestoreShipment(
                        trackingNumber = request.trackingNumber,
                        clientCode = request.clientCode,
                        clientName = request.clientName,
                        weight = request.grossWeight ?: 0.0,
                        withBox = request.withBox?.toString() ?: "0",
                        // createdAt and dateString are filled automatically in the model
                    )

                    // Save to Firestore
                    firebaseManager.saveShipment(
                        shipment = shipment,
                        onSuccess = {
                            uiScope.launch {
                                onSuccess()
                            }
                        },
                        onFailure = { e ->
                            uiScope.launch {
                                onError("API OK, но ошибка Firestore: ${e.message}")
                            }
                        }
                    )
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

    // Add a separate method if Toast is needed inside this class
    private fun showToast(message: String) {
        uiScope.launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
