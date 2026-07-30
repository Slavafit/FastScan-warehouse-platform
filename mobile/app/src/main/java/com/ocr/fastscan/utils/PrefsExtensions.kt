package com.ocr.fastscan.utils

import android.content.Context
import androidx.preference.PreferenceManager

fun Context.getVolWeightDivider(): Double {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val valueStr = prefs.getString("vol_weight", "6000") ?: "6000"
    return valueStr.toDoubleOrNull() ?: 6000.0
}

fun Context.saveVolWeightDivider(value: Double) {
    PreferenceManager.getDefaultSharedPreferences(this)
        .edit()
        .putString("vol_weight", value.toString())
        .apply()
}

fun Context.getBoxTareWeight(): Double {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val valueStr = prefs.getString("box_tare_weight", "0.8") ?: "0.8"
    return valueStr.replace(',', '.').toDoubleOrNull() ?: 0.8
}

fun Context.saveBoxTareWeight(value: Double) {
    PreferenceManager.getDefaultSharedPreferences(this)
        .edit()
        .putString("box_tare_weight", value.toString())
        .apply()
}
