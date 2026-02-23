package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.data.model.BoxEntry
import com.ocr.fastscan.data.model.FirestoreShipment
import java.security.Timestamp

class FirebaseManager(requireContext: Context) {
    private val db = FirebaseFirestore.getInstance()

    fun checkTrackExists(tracking: String, onResult: (Boolean) -> Unit) {
        db.collection("shipments") // Убедитесь, что имя коллекции совпадает
            .whereEqualTo("tracking", tracking)
            .get()
            .addOnSuccessListener { documents ->
                onResult(!documents.isEmpty) // Возвращает true, если список документов не пуст
            }
            .addOnFailureListener {
                onResult(false) // В случае ошибки считаем, что не нашли
            }
    }



    fun saveShipment(
        shipment: FirestoreShipment,    // ← основной параметр — объект с данными
        onSuccess: () -> Unit,          // колбэк успеха
        onFailure: (Exception) -> Unit) // колбэк ошибки
    {
        db.collection("shipments")
            .document(shipment.trackingNumber) // Устанавливаем ID документа равным номеру трека
            .set(shipment) // .set перезапишет данные, если ID уже существует
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Data saved/updated in Firestore: ${shipment.trackingNumber}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving data", e)
                onFailure(e)
            }
    }
    fun saveBoxEntry(entry: BoxEntry) {
        db.collection("Boxes")
            .add(entry)
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Box entry saved: order ${entry.orderTracking} in box ${entry.boxTracking}")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving box entry", e)
            }
    }
}