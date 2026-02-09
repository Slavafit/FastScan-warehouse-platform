package com.ocr.fastscan.data.model

import com.google.gson.annotations.SerializedName

// Ответ при логине
data class AuthResponse(
    @SerializedName("bearer") val bearer: String?,
    @SerializedName("refresh_bearer") val refreshBearer: String?
)

// Данные из GET запроса по треку (приходит массив [])
data class OrderDetails(
    @SerializedName("client_code") val clientCode: String?,
    @SerializedName("client") val clientName: String?,
    @SerializedName("with_box") val withBox: String?, // Приходит "1", "0" или null
    @SerializedName("gross_weight") val grossWeight: Double?
)

// Тело для финального POST запроса
data class SaveOrderRequest(
    @SerializedName("client_code") val clientCode: String,
    @SerializedName("client") val clientName: String,
    @SerializedName("tracking_number") val trackingNumber: String,
    @SerializedName("gross_weight") val grossWeight: String,
    @SerializedName("with_box") val withBox: String
)

// Ответ при сохранении
data class SaveResponse(
    val success: Boolean
)