package com.ocr.fastscan.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.data.model.BoxEntry
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.SearchResult
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.toObject
import java.util.Date

class FirebaseManager() {
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

    suspend fun findOrderEverywhere(track: String): SearchResult {
        return try {
            // Выполняем запросы
            val shipmentSnap = db.collection("shipments")
                .whereEqualTo("trackingNumber", track)
                .limit(1)
                .get()
                .await()

            val boxSnap = db.collection("box_entries")
                .whereEqualTo("orderTracking", track)
                .limit(1)
                .get()
                .await()

            SearchResult(
                shipment = shipmentSnap.documents.firstOrNull()?.toObject<FirestoreShipment>(),
                boxEntry = boxSnap.documents.firstOrNull()?.toObject<BoxEntry>()
            )
        } catch (e: Exception) {
            // Если ошибка в запросе, возвращаем пустой результат
            Log.e("FirebaseSearch", "Ошибка конвертации: ${e.message}", e)
            SearchResult(null, null)
        }
    }
    // Получить ВСЕ зарегистрированные заказы
    suspend fun getAllShipments(): List<FirestoreShipment> {
        return try {
            val snapshot = db.collection("shipments")
                .get()
                .await()
            snapshot.toObjects(FirestoreShipment::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Ошибка получения shipments", e)
            emptyList()
        }
    }

    // Получить ВСЕ записи об упаковке в коробки
    suspend fun getAllBoxEntries(): List<BoxEntry> {
        return try {
            val snapshot = db.collection("box_entries")
                .get()
                .await()
            snapshot.toObjects(BoxEntry::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Ошибка получения box_entries", e)
            emptyList()
        }
    }
    // Получить заказы (shipments) после определённой даты
    suspend fun getShipmentsAfter(date: Date): List<FirestoreShipment> {
        return try {
            val snapshot = db.collection("shipments")
                .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(date))
                .get()
                .await()

            snapshot.toObjects(FirestoreShipment::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Получить записи об упаковке после определённой даты
    suspend fun getBoxEntriesAfter(date: Date): List<BoxEntry> {
        return try {
            val snapshot = db.collection("box_entries")
                .whereGreaterThanOrEqualTo("date", com.google.firebase.Timestamp(date))
                .get()
                .await()

            snapshot.toObjects(BoxEntry::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}