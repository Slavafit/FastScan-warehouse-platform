package com.ocr.fastscan.data.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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