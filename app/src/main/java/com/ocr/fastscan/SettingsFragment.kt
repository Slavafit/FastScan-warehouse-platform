package com.ocr.fastscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.ocr.fastscan.utils.LicenseCheckResult
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.LicenseStatus
import com.ocr.fastscan.utils.saveVolWeightDivider
import com.ocr.fastscan.utils.ThemeHelper
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.normalizeEmail
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.preference.ListPreference

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        // Смена языка
        findPreference<ListPreference>("language")?.setOnPreferenceChangeListener { _, newValue ->
            val lang = newValue as String
            LanguageHelper.saveLanguage(requireContext(), lang)
            restartApplicationSafely()
            true
        }

        // Смена темы
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            ThemeHelper.applyTheme(theme)
            true
        }

        // Тест печати
        findPreference<Preference>("test_print")?.setOnPreferenceClickListener {
            val printHelper = PrintHelper(requireActivity())
            printHelper.printTestLabel()
            true
        }

        // Остальной код настроек
        findPreference<Preference>("clear_token")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_token_title)
                .setMessage(R.string.clear_token_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    AppDependencies.tokenManager.clear()
                    Toast.makeText(requireContext(), R.string.token_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }

        val volWeightPref = findPreference<EditTextPreference>("vol_weight")
        volWeightPref?.setOnPreferenceChangeListener { _, newValue ->
            val newDivider = (newValue as? String)?.toDoubleOrNull() ?: 6000.0
            requireContext().saveVolWeightDivider(newDivider)
            true
        }

        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }

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
            emailPref.summary = if (currentEmail.isEmpty()) {
                "Введите email, привязанный к лицензии"
            } else {
                currentEmail
            }

            emailPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val newEmail = newValue.toString().normalizeEmail()
                emailPref.text = newEmail
                emailPref.summary = if (newEmail.isEmpty()) {
                    "Введите email, привязанный к лицензии"
                } else {
                    newEmail
                }
                false
            }
        }

        findPreference<Preference>("developer_info")?.setOnPreferenceClickListener {
            val url = "https://t.me/slavafit"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>("license_status")?.let { licensePref ->
            updateLicenseStatus(licensePref)
        }
    }

    private fun updateLicenseStatus(licensePref: Preference) {
        lifecycleScope.launch {
            val status = getLicenseStatus()
            licensePref.summary = status
        }
    }

    private suspend fun getLicenseStatus(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val email = prefs.getString("user_email", "").orEmpty().normalizeEmail()

        if (email.isBlank()) {
            return getString(R.string.license_email_missing)
        }

        val checker = LicenseChecker(email)
        val licenseResult = checker.checkLicense(requireContext())

        return licenseResult.toStatusText()
/*
        } catch (e: Exception) {
            Log.e("Settings", "Ошибка при чтении лицензии", e)
            getString(R.string.license_check_error)
        }
    }

    // Полный перезапуск приложения
*/
    }

    private fun LicenseCheckResult.toStatusText(): String {
        return when (status) {
            LicenseStatus.TRIAL -> {
                val formattedDate = formatLicenseDate(expiresAt)
                getString(R.string.license_trial_status, daysLeft(expiresAt), formattedDate)
            }
            LicenseStatus.ACTIVE -> {
                if (expiresAt == 0L) getString(R.string.license_active_full_status)
                else getString(R.string.license_active_until_status, formatLicenseDate(expiresAt), daysLeft(expiresAt))
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

    private fun restartApplicationSafely() {
        val intent = requireActivity().intent
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK)

        requireActivity().finishAffinity()
        startActivity(intent)
    }
}
