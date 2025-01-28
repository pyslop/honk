package com.example.honk

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.animation.ObjectAnimator
import android.view.ViewConfiguration

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private var currentSoundResourceId = R.raw.honk_sound
    private var soundPool: SoundPool? = null
    private var honkSoundId = 0
    private var currentStreamId = 0
    private var isPlaying = false
    
    // Sound modulation ranges
    private val minRate = 0.5f
    private val maxRate = 2.0f
    private val minVolume = 0.2f
    private val maxVolume = 1.0f

    private val SCALE_PRESSED = 0.95f
    private val SCALE_NORMAL = 1.0f
    private val SCALE_ANIMATION_DURATION = 100L
    private var scaleDownAnimator: ObjectAnimator? = null
    private var scaleUpAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToolbarAndDrawer()
        initializeSoundPool()
        setupHonkButton()
    }

    private fun setupToolbarAndDrawer() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView: NavigationView = findViewById(R.id.navView)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sound_goose -> currentSoundResourceId = R.raw.honk_sound
                R.id.sound_duck -> currentSoundResourceId = R.raw.duck_sound
                R.id.sound_trumpet -> currentSoundResourceId = R.raw.trumpet_sound
            }
            // Reload sound
            soundPool?.unload(honkSoundId)
            honkSoundId = soundPool?.load(this, currentSoundResourceId, 1) ?: 0
            
            menuItem.isChecked = true
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupHonkButton() {
        val honkButton: AppCompatImageButton = findViewById(R.id.honkButton)
        val edgeSlop = ViewConfiguration.get(this).scaledEdgeSlop
        
        // Create scale animators
        scaleDownAnimator = ObjectAnimator.ofFloat(honkButton, "scaleX", SCALE_NORMAL, SCALE_PRESSED).apply {
            duration = SCALE_ANIMATION_DURATION
            addUpdateListener { 
                honkButton.scaleY = honkButton.scaleX
            }
        }
        
        scaleUpAnimator = ObjectAnimator.ofFloat(honkButton, "scaleX", SCALE_PRESSED, SCALE_NORMAL).apply {
            duration = SCALE_ANIMATION_DURATION
            addUpdateListener {
                honkButton.scaleY = honkButton.scaleX
            }
        }

        honkButton.setOnTouchListener { v, event ->
            // Ignore touches that start from screen edges
            if (event.action == MotionEvent.ACTION_DOWN && 
                (event.x <= edgeSlop || event.x >= v.width - edgeSlop ||
                 event.y <= edgeSlop || event.y >= v.height - edgeSlop)) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    scaleUpAnimator?.cancel()
                    scaleDownAnimator?.start()
                    startHonk(v, event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateHonk(v, event)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    scaleDownAnimator?.cancel()
                    scaleUpAnimator?.start()
                    stopHonk()
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.open()
            return true
        }
        return super.onOptionsItemSelected(item)
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
        if (isPlaying) {
            soundPool?.stop(currentStreamId)
        }
        
        v.isPressed = true
        val (volume, rate) = calculateSoundParameters(v, event)
        currentStreamId = soundPool?.play(honkSoundId, volume, volume, 1, -1, rate) ?: 0
        isPlaying = true
    }

    private fun updateHonk(v: android.view.View, event: MotionEvent) {
        if (!isPlaying) return
        val (volume, rate) = calculateSoundParameters(v, event)
        soundPool?.setVolume(currentStreamId, volume, volume)
        soundPool?.setRate(currentStreamId, rate)
    }

    private fun stopHonk() {
        if (!isPlaying) return
        
        soundPool?.stop(currentStreamId)
        currentStreamId = 0
        isPlaying = false
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
        if (isPlaying) {
            soundPool?.stop(currentStreamId)
        }
        soundPool?.release()
        soundPool = null
        scaleDownAnimator?.cancel()
        scaleUpAnimator?.cancel()
    }
}