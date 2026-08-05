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
        shipment: FirestoreShipment,    // ← main parameter: data object
        onSuccess: () -> Unit,          // success callback
        onFailure: (Exception) -> Unit) // failure callback
    {
        db.collection("shipments")
            .document(shipment.trackingNumber) // Set document ID to the tracking number
            .set(shipment) // .set overwrites data when the ID already exists
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
                 onSuccess: () -> Unit = {},          // success callback
                 onFailure: (Exception) -> Unit = {}     // failure callback)
    )    {
        val collection = db.collection("box_entries")  // collection name
            // Use tracking code as document ID; existing entries are overwritten
            collection.document(entry.orderTracking)  // tracking code as document ID
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
            // Execute queries
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
            // Return an empty result when the query fails
            Log.e("FirebaseSearch", "Ошибка конвертации: ${e.message}", e)
            SearchResult(null, null)
        }
    }
    // Get all registered orders
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

    // Get all box packing records
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
    // Get shipments after a specific date
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

    // Get packing records after a specific date
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