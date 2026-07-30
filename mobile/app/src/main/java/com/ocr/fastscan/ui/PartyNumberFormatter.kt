package com.ocr.fastscan.ui

object PartyNumberFormatter {
    fun normalize(value: String): String {
        val cleaned = value.trim().uppercase().replace("\\s+".toRegex(), "")
        if (cleaned.isEmpty()) return ""
        return if (cleaned.startsWith("KG")) cleaned else "KG$cleaned"
    }

    fun display(value: String): String {
        val trimmed = value.trim()
        val cleaned = trimmed.uppercase().replace("\\s+".toRegex(), "")
        if (cleaned.isEmpty()) return ""

        val isPartyNumber = cleaned.startsWith("KG") || cleaned.all { it.isDigit() }
        return if (isPartyNumber) normalize(cleaned) else trimmed
    }
}
