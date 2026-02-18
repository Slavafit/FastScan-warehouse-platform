package com.ocr.fastscan.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import com.ocr.fastscan.R

class SoundManager(private val context: Context) {

    private var successPlayer: MediaPlayer? = null

    init {
        // Инициализируем MediaPlayer один раз (чтобы не создавать каждый раз)
        try {
            successPlayer = MediaPlayer.create(context, R.raw.success)
            successPlayer?.setOnPreparedListener { it.isLooping = false }
            successPlayer?.setOnCompletionListener { it.seekTo(0) } // чтобы можно было перезапускать
        } catch (e: Exception) {
            Log.e("SoundManager", "Ошибка загрузки success.mp3", e)
        }
    }

    fun playScanBeep() {
        // Короткий пик (остаётся от ToneGenerator)
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    fun playSuccess() {
        try {
            successPlayer?.let { player ->
                if (player.isPlaying) {
                    player.seekTo(0)  // перезапускаем с начала
                }
                player.start()
            } ?: run {
                // Fallback, если файл не загрузился
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Ошибка воспроизведения success", e)
        }
    }

    fun playError() {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(ToneGenerator.TONE_SUP_ERROR, 500)
    }

    // Освобождаем ресурсы при уничтожении (вызывай в onDestroy MainActivity)
    fun release() {
        successPlayer?.release()
        successPlayer = null
    }
}