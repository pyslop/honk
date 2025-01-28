package com.example.honk

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton

class MainActivity : AppCompatActivity() {
    private var soundPool: SoundPool? = null
    private var honkSoundId = 0
    private var currentStreamId = 0
    private var lastVolume = 1.0f
    private var lastRate = 1.0f
    private val FADE_DURATION = 300L  // milliseconds
    
    // Sound modulation ranges
    private val minRate = 0.5f
    private val maxRate = 2.0f
    private val minVolume = 0.2f
    private val maxVolume = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeSoundPool()
        
        val honkButton = findViewById<AppCompatImageButton>(R.id.honkButton)
        honkButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startHonk(v, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateHonk(v, event)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopHonk()
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

    private fun startHonk(v: android.view.View, event: MotionEvent) {
        v.isPressed = true
        val (volume, rate) = calculateSoundParameters(v, event)
        lastVolume = volume
        lastRate = rate
        currentStreamId = soundPool?.play(honkSoundId, volume, volume, 1, -1, rate) ?: 0
    }

    private fun updateHonk(v: android.view.View, event: MotionEvent) {
        val (volume, rate) = calculateSoundParameters(v, event)
        lastVolume = volume
        lastRate = rate
        soundPool?.setVolume(currentStreamId, volume, volume)
        soundPool?.setRate(currentStreamId, rate)
    }

    private fun stopHonk() {
        // Start fade-out animation
        ValueAnimator.ofFloat(1.0f, 0.0f).apply {
            duration = FADE_DURATION
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                soundPool?.setVolume(currentStreamId, lastVolume * value, lastVolume * value)
            }
            start()
        }

        // Stop the sound after fade-out
        Handler(Looper.getMainLooper()).postDelayed({
            soundPool?.stop(currentStreamId)
            currentStreamId = 0
        }, FADE_DURATION)
    }

    private fun calculateSoundParameters(v: android.view.View, event: MotionEvent): Pair<Float, Float> {
        // Normalize x and y coordinates to 0-1 range
        val normalizedX = (event.x / v.width).coerceIn(0f, 1f)
        val normalizedY = (1 - event.y / v.height).coerceIn(0f, 1f) // Invert Y for intuitive control

        // Map normalized values to sound parameters
        val rate = normalizedX * (maxRate - minRate) + minRate
        val volume = normalizedY * (maxVolume - minVolume) + minVolume

        return Pair(volume, rate)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}