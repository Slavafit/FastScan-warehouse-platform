package com.ocr.fastscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.saveVolWeightDivider
import com.ocr.fastscan.utils.ThemeHelper
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
            val currentEmail = emailPref.text?.trim() ?: ""
            emailPref.summary = if (currentEmail.isEmpty()) {
                "Введите email, привязанный к лицензии"
            } else {
                currentEmail
            }

            emailPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val newEmail = newValue.toString().trim()
                emailPref.summary = if (newEmail.isEmpty()) {
                    "Введите email, привязанный к лицензии"
                } else {
                    newEmail
                }
                true
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
        val email = prefs.getString("user_email", "")?.trim()

        if (email.isNullOrBlank()) {
            return getString(R.string.license_email_missing)
        }

        val checker = LicenseChecker(email)
        val isValid = checker.isLicenseValid(requireContext())

        if (!isValid) {
            return getString(R.string.license_expired_or_inactive)
        }

        return try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("licenses").document(email).get().await()

            val expirationDate = doc.getTimestamp("expiration_date")?.toDate()
                ?: return getString(R.string.license_expiration_missing)

            val daysLeft = ((expirationDate.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()
            val formattedDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(expirationDate)

            when {
                daysLeft <= 0 -> getString(R.string.license_expired_status)
                daysLeft <= 7 -> getString(R.string.license_expires_soon_status, daysLeft, formattedDate)
                else -> getString(R.string.license_active_until_status, formattedDate, daysLeft)
            }
        } catch (e: Exception) {
            Log.e("Settings", "Ошибка при чтении лицензии", e)
            getString(R.string.license_check_error)
        }
    }

    // Полный перезапуск приложения
    private fun restartApplicationSafely() {
        val intent = requireActivity().intent
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK)

        requireActivity().finishAffinity()
        startActivity(intent)
    }
}
