package com.ocr.fastscan.utils


import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class LicenseChecker(private val userId: String) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun isLicenseValid(): Boolean {
        try {
            val doc = firestore.collection("licenses")
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val expirationDate = doc.getTimestamp("expiration_date")?.toDate() ?: return false
                val isActive = doc.getBoolean("is_active") ?: false

                return isActive && Date().before(expirationDate)
            }
        } catch (e: Exception) {
            Log.e("LicenseChecker", "Ошибка проверки лицензии: ${e.message}")
        }
        return false
    }
}