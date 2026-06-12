package com.ocr.fastscan.ui

import androidx.fragment.app.Fragment
import com.ocr.fastscan.api.OnzaApiService
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.FirebaseManager
import com.ocr.fastscan.utils.SoundManager
import com.ocr.fastscan.utils.TokenManager

/**
 * Базовый класс для всех фрагментов приложения.
 * Обеспечивает единый доступ к API и менеджерам из AppDependencies.
 */
abstract class BaseFragment : Fragment() {

    protected val apiService: OnzaApiService by lazy { AppDependencies.apiService }
    protected val firebaseManager: FirebaseManager by lazy { AppDependencies.firebaseManager }
    protected val soundManager: SoundManager by lazy { AppDependencies.soundManager }
    protected val tokenManager: TokenManager by lazy { AppDependencies.tokenManager }

    // Здесь можно будет позже добавить общие методы (toast, hideKeyboard и т.д.)
}
