package com.stelsuy.terminal.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

class VoicePlayer(context: Context) {

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<VoiceMessage, Int>()

    private val queue: ArrayDeque<VoiceMessage> = ArrayDeque()
    private var isPlaying = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1) // важливо: 1 стрім = без накладання
            .setAudioAttributes(attrs)
            .build()

        VoiceMessage.values().forEach { msg ->
            soundMap[msg] = soundPool.load(context, msg.resId, 1)
        }
    }

    /** Додати повідомлення в чергу */
    fun play(vararg messages: VoiceMessage) {
        messages.forEach { queue.add(it) }
        if (!isPlaying) playNext()
    }

    private fun playNext() {
        val msg = queue.poll() ?: run {
            isPlaying = false
            return
        }

        val soundId = soundMap[msg] ?: return
        isPlaying = true

        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)

        // ⏱️ Затримка між фразами (приблизна)
        val delayMs = estimateDuration(msg)
        handler.postDelayed({ playNext() }, delayMs)
    }

    // ⏱️ Орієнтовна тривалість (мс)
    private fun estimateDuration(msg: VoiceMessage): Long =
        when (msg) {
            VoiceMessage.SCAN_SUCCESS -> 700
            VoiceMessage.ACCESS_GRANTED -> 900
            VoiceMessage.SCAN_ERROR -> 900
            VoiceMessage.ACCESS_DENIED -> 900
            VoiceMessage.PLEASE_WAIT -> 800
            VoiceMessage.NO_CONNECTION -> 1200
        }

    fun stop() {
        queue.clear()
        isPlaying = false
    }

    fun release() {
        stop()
        soundPool.release()
    }
}