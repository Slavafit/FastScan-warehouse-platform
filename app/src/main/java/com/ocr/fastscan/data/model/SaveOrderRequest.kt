package com.ocr.fastscan.data.model

import com.google.gson.annotations.SerializedName

// Для отправки финального POST запроса
data class SaveOrderRequest(
    @SerializedName("client_code") val clientCode: String,
    @SerializedName("client") val clientName: String,
    @SerializedName("tracking_number") val trackingNumber: String,
    @SerializedName("gross_weight") val grossWeight: String,
    @SerializedName("with_box") val withBox: String
)