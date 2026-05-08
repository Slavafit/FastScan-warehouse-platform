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
import com.ocr.fastscan.ui.PartyFragment
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.ui.UnpackedOrdersFragment
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ПРОВЕРКА НАСТРОЕК (логин, пароль, email)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val login = prefs.getString("login", "")?.trim() ?: ""
        val password = prefs.getString("password", "")?.trim() ?: ""
        val email = prefs.getString("user_email", "")?.trim() ?: ""

        if (
            login.isBlank() || password.isBlank() || email.isBlank()) {
            Toast.makeText(this, "Введите логин, пароль и email в настройках", Toast.LENGTH_LONG).show()

            // Открываем настройки
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)

            // Закрываем MainActivity — после ввода данных приложение перезапустится
            finish()
            return
        }

        // Твоя проверка лицензии и авто-логин
        lifecycleScope.launch {
            val token = AppDependencies.tokenManager.getToken()
            if (token.isNullOrEmpty()) {
                //Log.d("Auth", "Токен отсутствует → выполняем авто-логин")
                AppDependencies.performAutoLogin(this@MainActivity)
            } else {
                Log.d("Auth", "Токен найден. Проверяем его валидность...")

                // Пока просто логируем. Позже можно добавить проверку токена на сервере
                // AppDependencies.checkTokenValidity(this@MainActivity)

                // Временное решение для отладки — можно раскомментировать, чтобы каждый раз логиниться:
                AppDependencies.tokenManager.clear()           // ← очищает токен
                AppDependencies.performAutoLogin(this@MainActivity)
            }
            if (AppDependencies.tokenManager.getToken() == null) {
                AppDependencies.performAutoLogin(this@MainActivity)
            } else {
                Log.d("Auth", "Токен уже есть, пропускаем авто-логин")
            }
            val checker = LicenseChecker(email)
            val isValid = checker.isLicenseValid(this@MainActivity)

            if (isValid) {
                // Лицензия OK — показываем фрагмент
                setupNavigation(savedInstanceState)

                // Рассчитываем и показываем оставшиеся дни
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("licenses").document(email).get().await()
                val expirationDate = doc.getTimestamp("expiration_date")?.toDate() ?: return@launch

                val daysLeft = ((expirationDate.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()

                val message = when {
                    daysLeft <= 0 -> "Лицензия истекла!"
                    daysLeft <= 7 -> "Осталось $daysLeft дней до окончания лицензии!"
                    daysLeft <= 30 -> "Лицензия активна (осталось $daysLeft дней)"
                    else -> "Лицензия активна"
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
                        replace(R.id.fragment_container, PartyFragment())
                    }
                    true
                }
                R.id.nav_unpacked -> {                    // ← Новый пункт
                    supportFragmentManager.commit {
                        replace(R.id.fragment_container, UnpackedOrdersFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }
}