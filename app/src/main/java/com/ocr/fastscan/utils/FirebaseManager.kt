package com.ocr.fastscan.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.data.model.FirestoreShipment

class FirebaseManager {
    private val db = FirebaseFirestore.getInstance()
    private val collectionPath = "shipments"

    fun saveShipment(
        shipment: FirestoreShipment,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection(collectionPath)
            .add(shipment)
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Data saved to Firestore: ${shipment.trackingNumber}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Error saving to Firestore", e)
                onFailure(e)
            }
    }
}