package com.example.honk

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton

class MainActivity : AppCompatActivity() {
    private var soundPool: SoundPool? = null
    private var honkSoundId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeSoundPool()
        
        val honkButton = findViewById<AppCompatImageButton>(R.id.honkButton)
        honkButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playHonk()
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun initializeSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()

        honkSoundId = soundPool?.load(this, R.raw.honk_sound, 1) ?: 0
    }

    private fun playHonk() {
        soundPool?.play(honkSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}