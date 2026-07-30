package com.ocr.fastscan.utils

import java.util.Locale

fun String.normalizeEmail(): String = trim().lowercase(Locale.ROOT)
