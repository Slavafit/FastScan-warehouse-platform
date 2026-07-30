package com.ocr.fastscan

import java.util.Locale

class ScanResultStabilizer(
    private val requiredMatches: Int = 3,
    private val maxGapMs: Long = 1_500L
) {
    private var candidate: String? = null
    private var matchCount = 0
    private var lastMatchTimeMs = 0L
    private var confirmed = false

    @Synchronized
    fun add(value: String): String? {
        if (confirmed) return null

        val normalized = value.trim().uppercase(Locale.ROOT)
        if (normalized.isEmpty()) return null

        val now = System.currentTimeMillis()
        val sameSeries = normalized == candidate && now - lastMatchTimeMs <= maxGapMs

        if (sameSeries) {
            matchCount++
        } else {
            candidate = normalized
            matchCount = 1
        }
        lastMatchTimeMs = now

        return if (matchCount >= requiredMatches) {
            confirmed = true
            normalized
        } else {
            null
        }
    }
}
