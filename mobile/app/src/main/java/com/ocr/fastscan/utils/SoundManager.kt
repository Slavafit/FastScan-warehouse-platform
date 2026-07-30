package com.ocr.fastscan.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.ocr.fastscan.R

class SoundManager(private val context: Context) {

    private var successPlayer: MediaPlayer? = null
    private var errorPlayer: MediaPlayer? = null
    private var printPlayer: MediaPlayer? = null
    private var scanPlayer: MediaPlayer? = null
    private var warningPlayer: MediaPlayer? = null
    private var sentPlayer: MediaPlayer? = null   // ← новый звук отправления

    init {
        loadSounds()
    }

    private fun loadSounds() {
        try {
            successPlayer = MediaPlayer.create(context, R.raw.success)
            errorPlayer   = MediaPlayer.create(context, R.raw.error)
            printPlayer   = MediaPlayer.create(context, R.raw.print)
            scanPlayer    = MediaPlayer.create(context, R.raw.scan_beep)
            warningPlayer = MediaPlayer.create(context, R.raw.warning)
            sentPlayer    = MediaPlayer.create(context, R.raw.sent)

            listOf(successPlayer, errorPlayer, printPlayer, scanPlayer, warningPlayer, sentPlayer).forEach { player ->
                player?.setOnPreparedListener { it.isLooping = false }
                player?.setOnCompletionListener { it.seekTo(0) }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Ошибка загрузки звуковых файлов", e)
        }
    }

    fun playSuccess() = playMediaPlayer(successPlayer, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    fun playError() {
        playMediaPlayer(errorPlayer, ToneGenerator.TONE_SUP_ERROR, 600)
        vibrateError()
    }
    fun playPrint()   = playMediaPlayer(printPlayer,   ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    fun playScanBeep()= playMediaPlayer(scanPlayer,    ToneGenerator.TONE_PROP_BEEP, 150)
    fun playWarning() = playMediaPlayer(warningPlayer, ToneGenerator.TONE_SUP_ERROR, 500)  // исправлено
    fun playSent()    = playMediaPlayer(sentPlayer,    ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)  // новый звук

    private fun playMediaPlayer(player: MediaPlayer?, fallbackTone: Int, fallbackDuration: Int) {
        try {
            player?.let { mp ->
                if (mp.isPlaying) mp.seekTo(0)
                mp.start()
            } ?: run {
                // Fallback на системный звук
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                tg.startTone(fallbackTone, fallbackDuration)
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Ошибка воспроизведения звука", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateError() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() != true) return

            val pattern = longArrayOf(0, 100, 70, 160)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing vibration", e)
        }
    }

    // Короткие вспомогательные сигналы
    fun playShortBeep() {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    fun release() {
        listOf(successPlayer, errorPlayer, printPlayer, scanPlayer, warningPlayer, sentPlayer).forEach {
            it?.release()
        }
        successPlayer = null
        errorPlayer = null
        printPlayer = null
        scanPlayer = null
        warningPlayer = null
        sentPlayer = null
    }
}
