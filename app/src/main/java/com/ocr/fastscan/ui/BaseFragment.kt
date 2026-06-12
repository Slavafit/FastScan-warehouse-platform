package com.ocr.fastscan.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.api.OnzaApiService
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.FirebaseManager
import com.ocr.fastscan.utils.SoundManager
import com.ocr.fastscan.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Базовый класс для всех фрагментов приложения.
 * Обеспечивает единый доступ к API и менеджерам из AppDependencies.
 */
abstract class BaseFragment : Fragment() {

    protected val apiService: OnzaApiService
        get() = AppDependencies.apiService

    protected val firebaseManager: FirebaseManager
        get() = AppDependencies.firebaseManager

    protected val soundManager: SoundManager
        get() = AppDependencies.soundManager

    protected val tokenManager: TokenManager
        get() = AppDependencies.tokenManager

    protected fun launchOnViewLifecycle(block: suspend CoroutineScope.() -> Unit): Job {
        return viewLifecycleOwner.lifecycleScope.launch(block = block)
    }

    protected fun showToast(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), messageRes, duration).show()
    }

    protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }

    // Здесь можно будет позже добавить общие методы (toast, hideKeyboard и т.д.)
}
