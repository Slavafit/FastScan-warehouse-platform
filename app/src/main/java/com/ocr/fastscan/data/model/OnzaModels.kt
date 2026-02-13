package com.ocr.fastscan.data.model

import com.google.gson.annotations.SerializedName
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Для авторизации
data class AuthResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("accessToken")
    val accessToken: String?,  // Именно так возвращает сервер

    @SerializedName("refreshToken")
    val refreshToken: String?, // Именно так возвращает сервер

    @SerializedName("role")
    val role: String?
)
// Данные из GET запроса по треку (приходит массив []) Для получения данных о клиенте
data class OrderDetails(
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("client") val clientName: String?,
    @SerializedName("with_box") val withBox: String?, // Приходит "1", "0" или null
    @SerializedName("gross_weight") val grossWeight: Double?
)
data class ReceiverResponse(
    @SerializedName("full_name") val fullName: String?
)

// Ответ при сохранении
data class SaveResponse(
    val success: Boolean
)
// Для простых ответов сервера (успех/ошибка)
data class SimpleResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?
)
data class FirestoreShipment(
    val trackingNumber: String = "",
    val clientCode: String = "",
    val clientName: String = "",
    val weight: Double = 0.0,
    val withBox: String = "0",
    val createdAt: Timestamp = Timestamp.now(),
    // Поле для удобной фильтрации в реестре (например, "2026-02-09")
    val dateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)