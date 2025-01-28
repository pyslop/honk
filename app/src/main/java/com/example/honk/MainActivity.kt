package com.example.honk

import android.animation.ObjectAnimator
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import java.io.File
import com.example.honk.data.AppDatabase
import com.example.honk.data.CustomSound
import com.example.honk.recorder.RecorderActivity
import android.net.Uri
import androidx.core.net.toUri
import java.io.IOException
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var db: AppDatabase
    private var currentSoundResourceId = R.raw.honk_sound
    private var currentCustomSoundPath: String? = null
    private var soundPool: SoundPool? = null
    private var honkSoundId = 0
    private var currentStreamId = 0
    private var isPlaying = false
    private var currentMenuItem: MenuItem? = null
    
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
        db = AppDatabase.getDatabase(this)

        setupToolbarAndDrawer()
        initializeSoundPool()
        setupHonkButton()
        setupRecorderFab()
        observeCustomSounds()
    }

    private fun observeCustomSounds() {
        lifecycleScope.launch {
            db.customSoundDao().getAllSounds().collect { sounds ->
                updateCustomSoundsMenu(sounds)
            }
        }
    }

    private fun updateCustomSoundsMenu(sounds: List<CustomSound>) {
        // Clear all existing custom sounds submenus
        val menu = navigationView.menu
        for (i in menu.size() - 1 downTo 0) {
            val item = menu.getItem(i)
            if (item.hasSubMenu() && item.title == "Custom Sounds") {
                menu.removeItem(item.itemId)
            }
        }

        // Add new submenu for custom sounds
        if (sounds.isNotEmpty()) {
            val customGroup = menu.addSubMenu("Custom Sounds")
            sounds.forEach { sound ->
                customGroup.add(R.id.group_custom_sounds, Menu.NONE, Menu.NONE, sound.name).apply {
                    setOnMenuItemClickListener {
                        loadCustomSound(sound)
                        currentMenuItem?.isChecked = false
                        it.isChecked = true
                        currentMenuItem = it
                        drawerLayout.closeDrawers()
                        true
                    }
                }
            }
        }
    }

    private fun loadCustomSound(sound: CustomSound) {
        try {
            val file = File(sound.filePath)
            if (!file.exists() || file.length() == 0L) {
                Log.e("MainActivity", "Sound file not found or empty at: ${sound.filePath}")
                showErrorDialog("Sound file not found", "The sound file has been moved, deleted, or is empty")
                lifecycleScope.launch {
                    db.customSoundDao().delete(sound)
                }
                return
            }

            // Verify file is readable
            if (!file.canRead()) {
                Log.e("MainActivity", "Cannot read sound file: ${sound.filePath}")
                showErrorDialog("File Error", "Cannot read the sound file")
                return
            }

            // Unload previous sound
            if (honkSoundId != 0) {
                Log.d("MainActivity", "Unloading previous sound ID: $honkSoundId")
                soundPool?.unload(honkSoundId)
                honkSoundId = 0
            }

            currentCustomSoundPath = sound.filePath
            currentSoundResourceId = 0
            
            // Direct file path loading
            Log.d("MainActivity", "Loading custom sound from path: ${sound.filePath}")
            honkSoundId = soundPool?.load(sound.filePath, 1) ?: 0
            Log.d("MainActivity", "Assigned new sound ID: $honkSoundId")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading sound", e)
            showErrorDialog("Error", "Failed to load sound file: ${e.message}")
            currentCustomSoundPath = null
            honkSoundId = soundPool?.load(this, R.raw.honk_sound, 1) ?: 0
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupToolbarAndDrawer() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navView)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            currentMenuItem?.isChecked = false
            menuItem.isChecked = true
            currentMenuItem = menuItem

            when (menuItem.itemId) {
                R.id.sound_goose -> {
                    currentSoundResourceId = R.raw.honk_sound
                    currentCustomSoundPath = null
                    reloadSound(currentSoundResourceId)
                }
                R.id.sound_duck -> {
                    currentSoundResourceId = R.raw.duck_sound
                    currentCustomSoundPath = null
                    reloadSound(currentSoundResourceId)
                }
                R.id.sound_trumpet -> {
                    currentSoundResourceId = R.raw.trumpet_sound
                    currentCustomSoundPath = null
                    reloadSound(currentSoundResourceId)
                }
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun reloadSound(resourceId: Int) {
        soundPool?.unload(honkSoundId)
        honkSoundId = soundPool?.load(this, resourceId, 1) ?: 0
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

    private fun setupRecorderFab() {
        findViewById<FloatingActionButton>(R.id.addSoundFab).setOnClickListener {
            val intent = Intent(this, RecorderActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.open()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    private fun initializeSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)  // Changed from GAME to MEDIA
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)  // Changed from SONIFICATION to MUSIC
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)  // Increased from 1
            .setAudioAttributes(attributes)
            .build()

        soundPool?.setOnLoadCompleteListener { soundPool, sampleId, status ->
            Log.d("MainActivity", "Global load complete - sampleId: $sampleId, status: $status")
        }

        honkSoundId = soundPool?.load(this, R.raw.honk_sound, 1) ?: 0
    }

    private fun startHonk(v: android.view.View, event: MotionEvent) {
        if (isPlaying) {
            Log.d("MainActivity", "Stopping previous sound")
            soundPool?.stop(currentStreamId)
        }
        
        v.isPressed = true
        val (volume, rate) = calculateSoundParameters(v, event)
        // Boost volume for custom sounds
        val boostedVolume = if (currentCustomSoundPath != null) volume * 2f else volume
        Log.d("MainActivity", "Playing sound (ID: $honkSoundId) with volume: $boostedVolume, rate: $rate")
        currentStreamId = soundPool?.play(honkSoundId, boostedVolume, boostedVolume, 2, 0, rate) ?: 0
        Log.d("MainActivity", "Started playing with stream ID: $currentStreamId")
        isPlaying = true
    }

    private fun updateHonk(v: android.view.View, event: MotionEvent) {
        if (!isPlaying) return
        val (volume, rate) = calculateSoundParameters(v, event)
        Log.d("MainActivity", "Updating sound (stream: $currentStreamId) - volume: $volume, rate: $rate")
        soundPool?.setVolume(currentStreamId, volume, volume)
        soundPool?.setRate(currentStreamId, rate)
    }

    private fun stopHonk() {
        if (!isPlaying) return
        
        Log.d("MainActivity", "Stopping sound (stream: $currentStreamId)")
        soundPool?.stop(currentStreamId)
        currentStreamId = 0
        isPlaying = false
    }

    private fun calculateSoundParameters(v: android.view.View, event: MotionEvent): Pair<Float, Float> {
        // Calculate distance from center of button
        val centerX = v.width / 2f
        val centerY = v.height / 2f
        val touchX = event.x
        val touchY = event.y
        
        // Convert to -1 to 1 range relative to center
        val normalizedX = ((touchX - centerX) / (v.width / 2f)).coerceIn(-1f, 1f)
        val normalizedY = ((centerY - touchY) / (v.height / 2f)).coerceIn(-1f, 1f)  // Invert Y for intuitive control
        
        // Rate calculation: center (0,0) = normal rate (1.0)
        // Left = slow (0.5), Right = fast (2.0)
        val rate = if (normalizedX >= 0) {
            1.0f + normalizedX * (maxRate - 1.0f)
        } else {
            1.0f + normalizedX * (1.0f - minRate)
        }
        
        // Volume increases as you move upward from center
        val volume = ((normalizedY + 1f) / 2f) * (maxVolume - minVolume) + minVolume

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