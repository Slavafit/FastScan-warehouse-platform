package com.ocr.fastscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.utils.LicenseChecker
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Загружаем XML настроек
            setPreferencesFromResource(R.xml.settings, rootKey)

            // Находим и обновляем статус лицензии
            findPreference<Preference>("license_status")?.let { licensePref ->
                updateLicenseStatus(licensePref)
            }

            // Находим поле email и обновляем его summary
            findPreference<EditTextPreference>("user_email")?.let { emailPref ->
                val currentEmail = emailPref.text?.trim() ?: ""
                emailPref.summary = if (currentEmail.isEmpty()) {
                    "Введите email, привязанный к лицензии"
                } else {
                    currentEmail
                }

                // Обновляем summary после изменения значения
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

            // Клик по "Разработчик" открывает Telegram
            findPreference<Preference>("developer_info")?.setOnPreferenceClickListener {
                val url = "https://t.me/slavafit"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                true
            }
        }

        override fun onResume() {
            super.onResume()
            // Обновляем статус лицензии при каждом открытии настроек
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
                return "Email не указан"
            }

            val checker = LicenseChecker(email)
            val isValid = checker.isLicenseValid(requireContext())

            if (!isValid) {
                return "Лицензия истекла или не активна"
            }

            return try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("licenses").document(email).get().await()

                val expirationDate = doc.getTimestamp("expiration_date")?.toDate()
                    ?: return "Дата истечения не указана"

                val daysLeft = ((expirationDate.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()
                val formattedDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(expirationDate)

                when {
                    daysLeft <= 0 -> "Лицензия истекла"
                    daysLeft <= 7 -> "Истекает через $daysLeft дней! (до $formattedDate)"
                    daysLeft <= 30 -> "Активна до $formattedDate (осталось $daysLeft дней)"
                    else -> "Активна до $formattedDate (осталось $daysLeft дней)"
                }
            } catch (e: Exception) {
                Log.e("Settings", "Ошибка при чтении лицензии", e)
                "Ошибка проверки статуса"
            }
        }
    }
}