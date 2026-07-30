package com.ocr.fastscan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.ui.BoxFragment
import com.ocr.fastscan.ui.PartyFragment
import com.ocr.fastscan.ui.ScanFragment
import com.ocr.fastscan.ui.UnpackedOrdersFragment
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.LicenseChecker
import com.ocr.fastscan.utils.LicenseCheckResult
import com.ocr.fastscan.utils.LicenseStatus
import com.ocr.fastscan.utils.LanguageHelper
import com.ocr.fastscan.utils.ThemeHelper
import com.ocr.fastscan.utils.normalizeEmail
import kotlinx.coroutines.launch
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Смена языка и темы при запуске приложения
    override fun attachBaseContext(newBase: Context) {
        // Сначала читаем настройки
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = prefs.getString("language", LanguageHelper.SYSTEM_LANGUAGE)
            ?: LanguageHelper.SYSTEM_LANGUAGE
        val theme = prefs.getString("theme", "system") ?: "system"

        // Применяем язык
        val updatedContext = LanguageHelper.applyLanguage(newBase, lang)

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
        val storedEmail = prefs.getString("user_email", "").orEmpty()
        val email = storedEmail.normalizeEmail()
        if (storedEmail != email) {
            prefs.edit().putString("user_email", email).apply()
        }

        if (login.isBlank() || password.isBlank() || email.isBlank()) {
            Toast.makeText(this, R.string.settings_required_message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // Авто-логин и проверка лицензии
        lifecycleScope.launch {
            val token = AppDependencies.tokenManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.d("Auth", "Токен отсутствует → авто-логин")
                val loginOk = AppDependencies.performAutoLogin(this@MainActivity)
                if (!loginOk) {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    return@launch
                }
            } else {
                Log.d("Auth", "Токен найден")
                // Можно добавить проверку валидности токена позже
            }

            val checker = LicenseChecker(email)
            val licenseResult = checker.checkLicense(this@MainActivity)

            if (licenseResult.hasAccess) {
                setupNavigation(savedInstanceState)

                // Показываем статус лицензии
                Toast.makeText(this@MainActivity, licenseResult.toUserMessage(), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, licenseResult.toUserMessage(), Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                finish()
            }
        }
    }

    private fun LicenseCheckResult.toUserMessage(): String {
        return when (status) {
            LicenseStatus.TRIAL -> getString(R.string.license_days_left_toast, daysLeft(expiresAt))
            LicenseStatus.ACTIVE -> {
                if (expiresAt == 0L) getString(R.string.license_active_full_status)
                else getString(R.string.license_active_days_left_toast, daysLeft(expiresAt))
            }
            LicenseStatus.NEED_ACTIVATION -> getString(R.string.license_activation_message)
            LicenseStatus.EXPIRED -> getString(R.string.license_expired_message)
            LicenseStatus.REVOKED -> getString(R.string.license_revoked_message)
            LicenseStatus.DEVICE_MISMATCH -> getString(R.string.license_device_mismatch_message)
            LicenseStatus.CONNECTION_ERROR -> getString(R.string.connection_error_message)
            LicenseStatus.INVALID -> getString(R.string.license_invalid_message)
        }
    }

    private fun daysLeft(expiresAt: Long): Int {
        if (expiresAt <= 0L) return 0
        return (((expiresAt * 1000) - Date().time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            navigateToTopLevel(R.id.nav_scan, clearBackStack = false)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            navigateToTopLevel(item.itemId)
        }

        binding.bottomNav.setOnItemReselectedListener { item ->
            navigateToTopLevel(item.itemId)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                if (binding.bottomNav.selectedItemId != R.id.nav_scan) {
                    binding.bottomNav.selectedItemId = R.id.nav_scan
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun navigateToTopLevel(itemId: Int, clearBackStack: Boolean = true): Boolean {
        val fragment = createTopLevelFragment(itemId) ?: return false

        if (clearBackStack) {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
        }

        return true
    }

    private fun createTopLevelFragment(itemId: Int): Fragment? {
        return when (itemId) {
            R.id.nav_scan -> ScanFragment()
            R.id.nav_boxes -> BoxFragment()
            R.id.nav_batches -> PartyFragment()
            R.id.nav_unpacked -> UnpackedOrdersFragment()
            else -> null
        }
    }
}
