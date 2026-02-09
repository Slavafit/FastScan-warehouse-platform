package com.ocr.fastscan.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager

class SoundManager(private val context: Context) {

    // Системный "пик" сканера
    fun playScanBeep() {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    // Успешное сохранение (можешь заменить на свой mp3 в папке res/raw)
    fun playSuccess() {
        // Если хочешь свой звук: MediaPlayer.create(context, R.raw.success_sound).start()
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
    }

    fun playError() {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tg.startTone(ToneGenerator.TONE_SUP_ERROR, 500)
    }
}