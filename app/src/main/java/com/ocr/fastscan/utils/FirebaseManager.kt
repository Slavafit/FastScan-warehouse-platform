package com.ocr.fastscan.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.data.model.BoxEntry
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.SearchResult

import java.security.Timestamp

class FirebaseManager(requireContext: Context) {
    private val db = FirebaseFirestore.getInstance()

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
    fun saveBoxEntry
                (entry: BoxEntry,
                 onSuccess: () -> Unit = {},          // колбэк успеха
                 onFailure: (Exception) -> Unit = {}     // колбэк ошибки)
    )    {
        val collection = db.collection("box_entries")  // твоя коллекция
            // Используем трек как ID документа — если запись уже есть, она перезапишется
            collection.document(entry.orderTracking)  // ← трек как document ID
            .set(entry)
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Box entry saved: order ${entry.orderTracking} in box ${entry.boxTracking}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving box entry", e)
                onFailure(e)
            }
    }

    fun performGlobalSearch(trackingNumber: String): SearchResult {
        val db = FirebaseFirestore.getInstance()

        // Запускаем оба запроса параллельно
        val shipmentTask = db.collection("shipments")
            .whereEqualTo("trackingNumber", trackingNumber)
            .limit(1)
            .get()

        val boxTask = db.collection("box_entries")
            .whereEqualTo("orderTracking", trackingNumber)
            .limit(1)
            .get()

        // Ждем выполнения обоих
        val shipmentSnap = shipmentTask.await()
        val boxSnap = boxTask.await()

        val shipment = shipmentSnap.documents.firstOrNull()?.toObject(FirestoreShipment::class.java)
        val boxEntry = boxSnap.documents.firstOrNull()?.toObject(BoxEntry::class.java)

        return SearchResult(shipment, boxEntry)
    }
}