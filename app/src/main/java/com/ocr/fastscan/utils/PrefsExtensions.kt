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