package com.ocr.fastscan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.ui.BatchFragment
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.LicenseChecker
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppDependencies.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val email = prefs.getString("user_email", "")?.trim() ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Введите email в настройках для активации лицензии", Toast.LENGTH_LONG).show()

            // Автоматически открываем настройки
            startActivity(Intent(this, SettingsActivity::class.java))

            // Закрываем MainActivity, чтобы пользователь вернулся после ввода
            finish()
            return
        }

        // Если email есть — проверяем лицензию
        lifecycleScope.launch {
            val checker = LicenseChecker(email)
            val isValid = checker.isLicenseValid()
            if (isValid) {
                setupNavigation(savedInstanceState)
            } else {
                Toast.makeText(this@MainActivity, "Лицензия истекла или не активна", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun checkLicense(email: String): Boolean {
        Log.d("LicenseCheck", "Проверка для email: $email")

        val db = FirebaseFirestore.getInstance()

        return try {
            val doc = db.collection("licenses")
                .document(email)
                .get()
                .await()

            Log.d("LicenseCheck", "Документ существует: ${doc.exists()}")

            if (doc.exists()) {
                val expirationTimestamp = doc.getTimestamp("expiration_date")
                val expirationDate = expirationTimestamp?.toDate()
                val isActive = doc.getBoolean("is_active") ?: false

                Log.d("LicenseCheck", "expiration_date (Timestamp): $expirationTimestamp")
                Log.d("LicenseCheck", "expiration_date (Date): $expirationDate")
                Log.d("LicenseCheck", "Текущая дата: ${Date()}")
                Log.d("LicenseCheck", "is_active: $isActive")

                val isValid = isActive && expirationDate != null && Date().before(expirationDate)
                Log.d("LicenseCheck", "Лицензия действительна: $isValid")

                isValid
            } else {
                Log.d("LicenseCheck", "Документ не найден")
                false
            }
        } catch (e: Exception) {
            Log.e("LicenseCheck", "Ошибка проверки: ${e.message}", e)
            false
        }
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, ScanFragment())
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, ScanFragment())
                    }
                    true
                }
                R.id.nav_boxes -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, BoxFragment())
                    }
                    true
                }
                R.id.nav_batches -> {
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, BatchFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }
}