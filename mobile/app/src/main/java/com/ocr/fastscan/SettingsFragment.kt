package com.ocr.fastscan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.LicenseCheckResult
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.LicenseStatus
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.ThemeHelper
import com.ocr.fastscan.utils.normalizeEmail
import com.ocr.fastscan.utils.saveVolWeightDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<ListPreference>("language")?.setOnPreferenceChangeListener { _, newValue ->
            LanguageHelper.saveLanguage(requireContext(), newValue as String)
            restartApplicationSafely()
            true
        }

        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            ThemeHelper.applyTheme(newValue as String)
            true
        }

        findPreference<Preference>("test_print")?.setOnPreferenceClickListener {
            PrintHelper(requireActivity()).printTestLabel()
            true
        }

        findPreference<Preference>("clear_token")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_token_title)
                .setMessage(R.string.clear_token_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    AppDependencies.tokenManager.clear()
                    updateAuthStatus()
                    Toast.makeText(requireContext(), R.string.token_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }

        findPreference<Preference>("use_new_test_api")?.setOnPreferenceClickListener {
            val apiUrlPreference = findPreference<EditTextPreference>("api_url")
            apiUrlPreference?.text = NEW_TEST_API_URL
            apiUrlPreference?.summary = NEW_TEST_API_URL
            AppDependencies.tokenManager.clear()
            updateAuthStatus()
            updateApiStatus()
            Toast.makeText(requireContext(), R.string.new_test_api_applied, Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("api_status")?.setOnPreferenceClickListener {
            updateApiStatus()
            true
        }

        setupPasswordPreference()

        findPreference<EditTextPreference>("vol_weight")?.setOnPreferenceChangeListener { _, newValue ->
            val newDivider = (newValue as? String)?.toDoubleOrNull() ?: 6000.0
            requireContext().saveVolWeightDivider(newDivider)
            true
        }

        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }

        updateAuthStatus()
        updateApiStatus()

        setupEmailPreference()

        findPreference<Preference>("developer_info")?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_URL)))
            true
        }

        findPreference<Preference>("app_version")?.summary = getString(
            R.string.app_version_summary,
            getAppVersionName(),
            getAppVersionCode()
        )
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }
        updateAuthStatus()
        updateApiStatus()
    }

    private fun setupPasswordPreference() {
        findPreference<EditTextPreference>("password")?.let { passwordPreference ->
            updatePasswordSummary(passwordPreference, passwordPreference.text.orEmpty())
            passwordPreference.setOnPreferenceChangeListener { _, newValue ->
                AppDependencies.tokenManager.clear()
                updatePasswordSummary(passwordPreference, newValue?.toString().orEmpty())
                updateAuthStatus()
                true
            }
        }
    }

    private fun updatePasswordSummary(preference: EditTextPreference, password: String) {
        preference.summary = if (password.isBlank()) {
            getString(R.string.password_not_set)
        } else {
            getString(R.string.password_saved)
        }
    }

    private fun updateAuthStatus() {
        val preference = findPreference<Preference>("auth_status") ?: return
        val tokenExists = !AppDependencies.tokenManager.getToken().isNullOrBlank()
        val login = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("login", "")
            .orEmpty()
            .trim()

        preference.summary = if (tokenExists) {
            getString(R.string.auth_status_signed_in, login.ifBlank { "—" })
        } else {
            getString(R.string.auth_status_signed_out)
        }
    }

    private fun updateApiStatus() {
        val preference = findPreference<Preference>("api_status") ?: return
        preference.summary = getString(R.string.api_status_checking)
        preference.isEnabled = false

        lifecycleScope.launch {
            val isReady = try {
                val response = AppDependencies.run {
                    NetworkModule.createService(requireContext(), includeAuth = false).getApiHealth()
                }
                response.isSuccessful && response.body()?.status == "ok"
            } catch (e: Exception) {
                Log.e("Settings", "API health check failed", e)
                false
            }

            if (!isAdded) return@launch
            preference.summary = coloredStatus(
                if (isReady) R.color.app_primary else R.color.delete_icon_tint,
                getString(if (isReady) R.string.api_status_online else R.string.api_status_offline)
            )
            preference.isEnabled = true
        }
    }

    private fun coloredStatus(colorRes: Int, text: String): CharSequence {
        val status = SpannableString("● $text")
        status.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), colorRes)),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return status
    }

    private fun setupEmailPreference() {
        findPreference<EditTextPreference>("user_email")?.let { emailPref ->
            emailPref.setOnBindEditTextListener { editText ->
                editText.filters = editText.filters + android.text.InputFilter { source, _, _, _, _, _ ->
                    val normalized = source.toString().lowercase(Locale.ROOT)
                    if (normalized == source.toString()) null else normalized
                }
            }

            val currentEmail = emailPref.text.orEmpty().normalizeEmail()
            if (emailPref.text != currentEmail) {
                emailPref.text = currentEmail
            }
            updateEmailSummary(emailPref, currentEmail)

            emailPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val newEmail = newValue.toString().normalizeEmail()
                emailPref.text = newEmail
                updateEmailSummary(emailPref, newEmail)
                false
            }
        }
    }

    private fun updateEmailSummary(emailPref: EditTextPreference, email: String) {
        emailPref.summary = email.ifEmpty { getString(R.string.licence_email_summary) }
    }

    private fun updateLicenseStatus(licensePref: Preference) {
        lifecycleScope.launch {
            licensePref.summary = getLicenseStatus()
        }
    }

    private suspend fun getLicenseStatus(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val email = prefs.getString("user_email", "").orEmpty().normalizeEmail()

        if (email.isBlank()) {
            return getString(R.string.license_email_missing)
        }

        return try {
            LicenseChecker(email).checkLicense(requireContext()).toStatusText()
        } catch (e: Exception) {
            Log.e("Settings", "License status check failed", e)
            getString(R.string.license_check_error)
        }
    }

    private fun LicenseCheckResult.toStatusText(): String {
        return when (status) {
            LicenseStatus.TRIAL -> {
                val formattedDate = formatLicenseDate(expiresAt)
                getString(R.string.license_trial_status, daysLeft(expiresAt), formattedDate)
            }
            LicenseStatus.ACTIVE -> {
                if (expiresAt == 0L) {
                    getString(R.string.license_active_full_status)
                } else {
                    getString(R.string.license_active_until_status, formatLicenseDate(expiresAt), daysLeft(expiresAt))
                }
            }
            LicenseStatus.NEED_ACTIVATION -> getString(R.string.license_need_activation_detail)
            LicenseStatus.EXPIRED -> getString(R.string.license_expired_message)
            LicenseStatus.REVOKED -> getString(R.string.license_revoked_message)
            LicenseStatus.DEVICE_MISMATCH -> getString(R.string.license_device_mismatch_message)
            LicenseStatus.CONNECTION_ERROR -> getString(R.string.connection_error_message)
            LicenseStatus.INVALID -> getString(R.string.license_invalid_message)
        }
    }

    private fun formatLicenseDate(expiresAt: Long): String {
        if (expiresAt <= 0L) return "-"
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expiresAt * 1000))
    }

    private fun daysLeft(expiresAt: Long): Int {
        if (expiresAt <= 0L) return 0
        return (((expiresAt * 1000) - Date().time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun getAppVersionName(): String {
        return requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName ?: "-"
    }

    private fun getAppVersionCode(): Long {
        val packageInfo = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun restartApplicationSafely() {
        val intent = requireActivity().intent
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        )

        requireActivity().finishAffinity()
        startActivity(intent)
    }

    companion object {
        private const val DEVELOPER_URL = "https://t.me/slavafit"
        private const val NEW_TEST_API_URL = "http://10.0.2.2:8080"
    }
}
