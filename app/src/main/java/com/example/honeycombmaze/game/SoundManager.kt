package com.example.honeycombmaze.game

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SoundManager {
    private var toneGenerator: ToneGenerator? = null

    fun init() {
        if (toneGenerator == null) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    fun playMoveSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    fun playWallHitSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 40)
    }

    fun playWinSound() {
        CoroutineScope(Dispatchers.Default).launch {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 150)
            delay(150)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 200)
            delay(200)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 300)
        }
    }
    
    fun playHoneyCollectSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
    }

    fun playGameOverSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500)
    }

    fun playTrapSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
    }

    fun playTeleportSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
    }
}
