package com.ocr.fastscan.utils


import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import android.provider.Settings

class LicenseChecker(private val userId: String) {


    suspend fun isLicenseValid(context: Context): Boolean {
        val email = userId.trim()  // userId = email из настроек
        if (email.isEmpty()) {
            //Log.d("LicenseCheck", "Email пустой")
            return false
        }

        val deviceId = getDeviceId(context)
        //Log.d("LicenseCheck", "Email: '$email' | Device ID: '$deviceId'")

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("licenses").document(email)

        return try {
            val doc = docRef.get().await()

            if (!doc.exists()) {
                // Документа нет — создаём новый
                val newLicense = hashMapOf(
                    "email" to email,
                    "device_id" to deviceId,
                    "is_active" to true,
                    "expiration_date" to com.google.firebase.Timestamp(Date(2026 - 1900, 11, 31, 23, 59, 59)) // 31.12.2026 23:59:59
                )

                docRef.set(newLicense).await()
                //Log.d("LicenseCheck", "Создан новый документ лицензии для $email с device_id: $deviceId")
                true  // Только что создали — считаем валидным
            } else {
                // Документ существует — проверяем
                val storedDeviceId = doc.getString("device_id") ?: ""
                //автоматическое обновление device_id
                if (storedDeviceId != deviceId) {
                    // Если device_id не совпадает — обновляем его
                    docRef.update("device_id", deviceId).await()
                    //Log.d("LicenseCheck", "device_id обновлён на: $deviceId")
                }
                val isActive = doc.getBoolean("is_active") ?: false
                val expirationDate = doc.getTimestamp("expiration_date")?.toDate()

                val deviceMatch = storedDeviceId == deviceId
                val dateValid = expirationDate != null && Date().before(expirationDate)

//                Log.d("LicenseCheck", "Device match: $deviceMatch")
//                Log.d("LicenseCheck", "is_active: $isActive")
//                Log.d("LicenseCheck", "expiration_date: $expirationDate")
//                Log.d("LicenseCheck", "Текущая дата: ${Date()}")
//                Log.d("LicenseCheck", "Дата валидна: $dateValid")

                isActive && deviceMatch && dateValid
            }
        } catch (e: Exception) {
            Log.e("LicenseCheck", "Ошибка проверки лицензии: ${e.message}", e)
            false
        }
    }
    //Получение device_id
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"  // fallback, если вдруг не удалось
    }
}