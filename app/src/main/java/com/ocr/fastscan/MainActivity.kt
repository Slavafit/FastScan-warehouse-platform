package com.ocr.fastscan

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.ui.BatchFragment
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.utils.AppDependencies
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

        // Проверка лицензии асинхронно
        lifecycleScope.launch {
            val isValid = checkLicense()
            if (isValid) {
                setupNavigation(savedInstanceState)
            } else {
                finish()
            }
        }
    }

    private suspend fun checkLicense(): Boolean {
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val email = prefs.getString("user_email", "")?.trim() ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Введите email в настройках для активации лицензии", Toast.LENGTH_LONG).show()
            return false
        }

        val db = FirebaseFirestore.getInstance()

        return try {
            val doc = db.collection("licenses")
                .document(email)
                .get()
                .await()

            if (doc.exists()) {
                val expirationDate = doc.getTimestamp("expiration_date")?.toDate()
                val isActive = doc.getBoolean("is_active") ?: false

                isActive && expirationDate != null && Date().before(expirationDate)
            } else {
                Toast.makeText(this, "Лицензия не найдена для $email", Toast.LENGTH_LONG).show()
                false
            }
        } catch (e: Exception) {
            Log.e("LicenseCheck", "Ошибка проверки: ${e.message}", e)
            Toast.makeText(this, "Не удалось проверить лицензию. Проверьте интернет.", Toast.LENGTH_LONG).show()
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