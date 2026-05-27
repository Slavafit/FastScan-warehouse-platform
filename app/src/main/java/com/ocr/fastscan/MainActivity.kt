package com.ocr.fastscan

import android.content.Context
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
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.PartyFragment
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.ui.UnpackedOrdersFragment
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.updateLocale
import com.ocr.fastscan.utils.ThemeHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Смена языка и темы при запуске приложения
    override fun attachBaseContext(newBase: Context) {
        // Сначала читаем настройки
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = prefs.getString("language", "system") ?: "system"
        val theme = prefs.getString("theme", "system") ?: "system"

        // Применяем язык
        val updatedContext = newBase.updateLocale(lang)

        // Применяем тему (важно делать до super)
        ThemeHelper.applyTheme(theme)

        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val login = prefs.getString("login", "")?.trim() ?: ""
        val password = prefs.getString("password", "")?.trim() ?: ""
        val email = prefs.getString("user_email", "")?.trim() ?: ""

        if (login.isBlank() || password.isBlank() || email.isBlank()) {
            Toast.makeText(this, "Введите логин, пароль и email в настройках", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // Авто-логин и проверка лицензии
        lifecycleScope.launch {
            val token = AppDependencies.tokenManager.getToken()

            if (token.isNullOrEmpty()) {
                AppDependencies.performAutoLogin(this@MainActivity)
            }

            val checker = LicenseChecker(email)
            val isValid = checker.isLicenseValid(this@MainActivity)

            if (isValid) {
                setupNavigation(savedInstanceState)

                // Показываем статус лицензии
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("licenses").document(email).get().await()
                val expirationDate = doc.getTimestamp("expiration_date")?.toDate() ?: return@launch

                val daysLeft = ((expirationDate.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()

                val message = when {
                    daysLeft <= 0 -> "Лицензия истекла!"
                    daysLeft <= 7 -> "Осталось $daysLeft дней до окончания лицензии!"
                    else -> "Лицензия активна (осталось $daysLeft дней)"
                }

                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Лицензия истекла или не активна", Toast.LENGTH_LONG).show()
                finish()
            }
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
                    supportFragmentManager.commit { replace(R.id.fragment_container, ScanFragment()) }
                    true
                }
                R.id.nav_boxes -> {
                    supportFragmentManager.commit { replace(R.id.fragment_container, BoxFragment()) }
                    true
                }
                R.id.nav_batches -> {
                    supportFragmentManager.commit { replace(R.id.fragment_container, PartyFragment()) }
                    true
                }
                R.id.nav_unpacked -> {
                    supportFragmentManager.commit { replace(R.id.fragment_container, UnpackedOrdersFragment()) }
                    true
                }
                else -> false
            }
        }
    }
}