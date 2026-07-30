package com.ocr.fastscan.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class LicenseChecker(private val userId: String) {

    suspend fun isLicenseValid(context: Context): Boolean {
        return checkLicense(context).hasAccess
    }

    suspend fun checkLicense(context: Context): LicenseCheckResult {
        val email = userId.normalizeEmail()
        if (email.isEmpty()) {
            return LicenseCheckResult(LicenseStatus.NEED_ACTIVATION)
        }

        val deviceId = getDeviceId(context)
        val appId = context.packageName
        val appName = getAppName(context)
        Log.d(
            "LicenseCheck",
            "Checking license: email=${email.maskEmail()}, device=${deviceId.maskDeviceId()}, app_id=$appId, app_name=$appName"
        )

        return try {
            val response = requestLicense(email, deviceId, appId, appName)
            saveLicenseToken(context, response.token)
            response.toResult()
        } catch (e: Exception) {
            Log.e("LicenseCheck", "License server check failed", e)
            LicenseCheckResult(LicenseStatus.CONNECTION_ERROR, errorMessage = e.localizedMessage)
        }
    }

    private suspend fun requestLicense(
        email: String,
        deviceId: String,
        appId: String,
        appName: String
    ): LicenseServerResponse {
        return withContext(Dispatchers.IO) {
            val postResponse = executePost(email, deviceId, appId, appName)
            if (postResponse != null) return@withContext postResponse

            executeGet(email, deviceId, appId, appName) ?: LicenseServerResponse(ok = false, status = "connection_error")
        }
    }

    private fun executePost(
        email: String,
        deviceId: String,
        appId: String,
        appName: String
    ): LicenseServerResponse? {
        val json = gson.toJson(
            mapOf(
                "email" to email,
                "device_id" to deviceId,
                "app_id" to appId,
                "app_name" to appName
            )
        )
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(LICENSE_SERVER_URL)
            .post(body)
            .build()

        Log.d("LicenseCheck", "Sending POST license check")
        return executeRequest(request)
    }

    private fun executeGet(
        email: String,
        deviceId: String,
        appId: String,
        appName: String
    ): LicenseServerResponse? {
        val encodedEmail = URLEncoder.encode(email, "UTF-8")
        val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8")
        val encodedAppId = URLEncoder.encode(appId, "UTF-8")
        val encodedAppName = URLEncoder.encode(appName, "UTF-8")
        val request = Request.Builder()
            .url("$LICENSE_SERVER_URL?email=$encodedEmail&device_id=$encodedDeviceId&app_id=$encodedAppId&app_name=$encodedAppName")
            .get()
            .build()

        Log.d("LicenseCheck", "Sending GET license check fallback")
        return executeRequest(request)
    }

    private fun executeRequest(request: Request): LicenseServerResponse? {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d("LicenseCheck", "${request.method} license response: HTTP ${response.code}, body=$body")

            if (!response.isSuccessful) {
                Log.w("LicenseCheck", "${request.method} license check failed: HTTP ${response.code}")
                return null
            }

            if (body.isBlank()) {
                Log.w("LicenseCheck", "${request.method} license check failed: empty response")
                return null
            }

            return gson.fromJson(body, LicenseServerResponse::class.java)
        }
    }

    private fun LicenseServerResponse.toResult(): LicenseCheckResult {
        val normalizedStatus = status.orEmpty().lowercase()
        if (!ok) {
            return LicenseCheckResult(normalizedStatus.toLicenseStatus())
        }

        val status = normalizedStatus.toLicenseStatus()
        val expiresAt = expires_at ?: 0L
        val nowSeconds = System.currentTimeMillis() / 1000
        val isExpired = expiresAt > 0L && nowSeconds >= expiresAt

        return when {
            status == LicenseStatus.ACTIVE && expiresAt == 0L -> LicenseCheckResult(status, token, expiresAt)
            status in ACCESS_STATUSES && !isExpired -> LicenseCheckResult(status, token, expiresAt)
            status in ACCESS_STATUSES -> LicenseCheckResult(LicenseStatus.EXPIRED, token, expiresAt)
            else -> LicenseCheckResult(status, token, expiresAt)
        }
    }

    private fun String.toLicenseStatus(): LicenseStatus {
        return when (this) {
            "trial" -> LicenseStatus.TRIAL
            "active" -> LicenseStatus.ACTIVE
            "expired" -> LicenseStatus.EXPIRED
            "revoked" -> LicenseStatus.REVOKED
            "device_mismatch" -> LicenseStatus.DEVICE_MISMATCH
            "connection_error" -> LicenseStatus.CONNECTION_ERROR
            "need_activation", "not_found", "missing_email" -> LicenseStatus.NEED_ACTIVATION
            else -> LicenseStatus.INVALID
        }
    }

    private fun saveLicenseToken(context: Context, token: String?) {
        if (token.isNullOrBlank()) return

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("license_token", token)
            .apply()
    }

    private fun getDeviceId(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.getString(KEY_LICENSE_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != BROKEN_ANDROID_ID }
            ?: UUID.randomUUID().toString()

        prefs.edit()
            .putString(KEY_LICENSE_DEVICE_ID, androidId)
            .apply()

        return androidId
    }

    private fun getAppName(context: Context): String {
        val applicationInfo = context.applicationInfo
        return context.packageManager.getApplicationLabel(applicationInfo).toString()
    }

    private fun String.maskEmail(): String {
        val atIndex = indexOf('@')
        if (atIndex <= 1) return "***"

        val name = take(atIndex)
        val domain = drop(atIndex)
        return "${name.take(1)}***$domain"
    }

    private fun String.maskDeviceId(): String {
        if (length <= 4) return "***"
        return "***${takeLast(4)}"
    }

    private data class LicenseServerResponse(
        val ok: Boolean = false,
        val status: String? = null,
        val token: String? = null,
        val expires_at: Long? = null
    )

    companion object {
        private const val LICENSE_SERVER_URL = "https://license-server.slavafit.workers.dev/activate"
        private const val KEY_LICENSE_DEVICE_ID = "license_device_id"
        private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ACCESS_STATUSES = setOf(LicenseStatus.TRIAL, LicenseStatus.ACTIVE)
        private val gson = Gson()
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

data class LicenseCheckResult(
    val status: LicenseStatus,
    val token: String? = null,
    val expiresAt: Long = 0L,
    val errorMessage: String? = null
) {
    val hasAccess: Boolean
        get() = status == LicenseStatus.TRIAL || status == LicenseStatus.ACTIVE
}

enum class LicenseStatus {
    NEED_ACTIVATION,
    TRIAL,
    ACTIVE,
    EXPIRED,
    REVOKED,
    DEVICE_MISMATCH,
    INVALID,
    CONNECTION_ERROR
}
