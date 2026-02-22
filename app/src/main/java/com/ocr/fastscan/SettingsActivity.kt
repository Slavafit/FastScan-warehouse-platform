package com.ocr.fastscan

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
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

        private lateinit var licensePref: Preference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings, rootKey)  // твой XML

            licensePref = findPreference("license_status")!!

            updateLicenseStatus()
        }

        override fun onResume() {
            super.onResume()
            updateLicenseStatus()  // обновляем при каждом возврате в настройки
        }

        private fun updateLicenseStatus() {
            lifecycleScope.launch {
                val status = getLicenseStatus()
                licensePref.summary = status
            }
        }

        private suspend fun getLicenseStatus(): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val email = prefs.getString("user_email", "")?.trim() ?: return "Email не указан"

            val checker = LicenseChecker(email)
            val isValid = checker.isLicenseValid(requireContext())

            return if (isValid) {
                try {
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
                    Log.e("Settings", "Ошибка чтения лицензии: ${e.message}")
                    "Ошибка проверки"
                }
            } else {
                "Лицензия истекла или не активна"
            }
        }
    }
}