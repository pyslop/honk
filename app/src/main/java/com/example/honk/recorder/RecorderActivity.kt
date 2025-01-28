package com.example.honk.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.honk.R
import com.example.honk.data.AppDatabase
import com.example.honk.data.CustomSound
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.animation.ObjectAnimator
import android.view.animation.AnimationUtils

class RecorderActivity : AppCompatActivity() {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private lateinit var db: AppDatabase
    private lateinit var adapter: RecordingAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var amplitudeChecker: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentDialog: AlertDialog? = null
    private var isRecordingSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_recorder)
            Log.d("RecorderActivity", "Activity created")
            
            db = AppDatabase.getDatabase(this)
            setupRecyclerView()
            setupRecordButton()

            if (!hasRecordPermission()) {
                Log.d("RecorderActivity", "Requesting permission")
                requestRecordPermission()
            } else {
                Log.d("RecorderActivity", "Permission already granted")
            }
        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error in onCreate", e)
            finish()
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = RecordingAdapter { sound ->
                showDeleteConfirmation(sound)
            }
            
            findViewById<RecyclerView>(R.id.recordingsRecyclerView)?.apply {
                layoutManager = LinearLayoutManager(this@RecorderActivity)
                adapter = this@RecorderActivity.adapter
            }

            // Observe recordings
            lifecycleScope.launch {
                db.customSoundDao().getAllSounds().collect { sounds ->
                    adapter.submitList(sounds)
                }
            }
        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error in setupRecyclerView", e)
        }
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION
        )
    }

    private fun setupRecordButton() {
        val recordButton = findViewById<View>(R.id.recordButton)
        val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.record_button_scale)
        
        recordButton?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    // Start pulsating animation
                    recordButton.startAnimation(scaleAnimation)
                    // Add red tint with animation
                    ObjectAnimator.ofArgb(
                        window.decorView,
                        "backgroundColor",
                        0x00000000,
                        0x33FF0000
                    ).apply {
                        duration = 300
                        start()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    // Clear animations
                    recordButton.clearAnimation()
                    ObjectAnimator.ofArgb(
                        window.decorView,
                        "backgroundColor",
                        0x33FF0000,
                        0x00000000
                    ).apply {
                        duration = 300
                        start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecording() {
        if (!hasRecordPermission()) return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "RECORD_$timestamp.3gp"
            recordingFile = File(getExternalFilesDir(null), fileName)
            // Ensure directory exists
            recordingFile?.parentFile?.mkdirs()
            
            Log.d("RecorderActivity", "Starting recording to: ${recordingFile?.absolutePath}")

            recorder = MediaRecorder().apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  // Better gain than CAMCORDER
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(320000)  // Increased to 320kbps for better quality
                    setOutputFile(recordingFile!!.absolutePath)
                    
                    prepare()
                    start()
                    isRecording = true
                    startAmplitudeMonitoring()
                    Log.d("RecorderActivity", "Recording started with voice optimization")
                } catch (e: Exception) {
                    Log.e("RecorderActivity", "Error configuring recorder", e)
                    showErrorDialog("Recording Error", "Failed to start recording: ${e.message}")
                    cleanup()
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error in startRecording", e)
            showErrorDialog("Recording Error", "Failed to initialize recording: ${e.message}")
            cleanup()
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeChecker = Runnable {
            try {
                val amplitude = recorder?.maxAmplitude ?: 0
                Log.d("RecorderActivity", "Current amplitude: $amplitude")
                
                // Visual feedback - update background color based on amplitude
                val normalizedAmplitude = (amplitude / 32768f).coerceIn(0f, 1f)
                val alpha = (normalizedAmplitude * 0x44).toInt()
                window.decorView.setBackgroundColor(alpha shl 24 or 0x00FF0000)
                
                if (isRecording) {
                    handler.postDelayed(amplitudeChecker!!, 100)
                }
            } catch (e: Exception) {
                Log.e("RecorderActivity", "Error reading amplitude", e)
            }
        }
        handler.post(amplitudeChecker!!)
    }

    private fun stopRecording() {
        try {
            Log.d("RecorderActivity", "Stopping recording")
            
            // Save file path and stop recording
            val finalRecordingFile = recordingFile
            
            try {
                recorder?.apply {
                    stop()
                    release()
                }
                recorder = null
                isRecording = false
                Log.d("RecorderActivity", "Recording stopped successfully")
            } catch (e: Exception) {
                Log.e("RecorderActivity", "Error stopping recorder", e)
                cleanup()
                return
            }

            // Make sure file exists before proceeding
            if (finalRecordingFile == null || !finalRecordingFile.exists()) {
                Log.e("RecorderActivity", "Recording file missing")
                cleanup()
                return
            }

            // Show save dialog immediately without processing
            showSaveDialog()

        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error in stopRecording", e)
            cleanup()
        }
    }

    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_recording, null)
        val input = dialogView.findViewById<EditText>(R.id.nameInput)
        val previewButton = dialogView.findViewById<Button>(R.id.previewButton)
        
        currentDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Save Recording")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Discard") { _, _ ->
                stopPreviewPlayback()
            }
            .create()

        // Setup preview button with touch listener for continuous playback
        previewButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startPreviewPlayback()
                    (v as Button).text = "Playing..."
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopPreviewPlayback()
                    (v as Button).text = "Preview"
                    true
                }
                else -> false
            }
        }

        currentDialog?.setOnShowListener {
            val positiveButton = currentDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val soundName = input.text.toString().takeIf { it.isNotEmpty() } ?: "Untitled"
                stopPreviewPlayback()
                saveRecording(soundName)
                currentDialog?.dismiss()
            }
        }

        currentDialog?.setOnDismissListener {
            stopPreviewPlayback()
            currentDialog = null
            if (recordingFile?.exists() == true && !isRecordingSaved) {
                cleanupFiles()
            }
        }

        currentDialog?.show()
    }

    private fun startPreviewPlayback() {
        try {
            stopPreviewPlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recordingFile!!.absolutePath)
                setVolume(1.0f, 1.0f)
                isLooping = true  // Enable looping
                prepare()
                start()
            }
            Log.d("RecorderActivity", "Started looping preview playback")
        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error playing preview", e)
        }
    }

    private fun stopPreviewPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        Log.d("RecorderActivity", "Stopped preview playback")
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        stopPreviewPlayback()
        // Only cleanup files if we haven't saved successfully
        if (!isRecordingSaved) {
            cleanupFiles()
        }
    }

    private fun saveRecording(name: String) {
        recordingFile?.let { file ->
            lifecycleScope.launch {
                try {
                    // First verify file exists and has content
                    if (!file.exists() || file.length() == 0L) {
                        throw IOException("Recording file is missing or empty")
                    }

                    val sound = CustomSound(
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        filePath = file.absolutePath
                    )
                    Log.d("RecorderActivity", "Saving recording at: ${file.absolutePath}")
                    db.customSoundDao().insert(sound)
                    isRecordingSaved = true
                    
                    // Verify file is still there after save
                    if (!file.exists()) {
                        throw IOException("File disappeared after saving")
                    }
                    
                    finish()
                } catch (e: Exception) {
                    Log.e("RecorderActivity", "Error saving recording", e)
                    runOnUiThread {
                        showErrorDialog("Error", "Failed to save recording: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation(sound: CustomSound) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete '${sound.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecording(sound)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(sound: CustomSound) {
        lifecycleScope.launch {
            db.customSoundDao().delete(sound)
            File(sound.filePath).delete()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION && 
            grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
            finish()
        }
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION = 123
    }

    private fun showErrorDialog(title: String, message: String) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this@RecorderActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun cleanup() {
        try {
            isRecording = false
            amplitudeChecker?.let { handler.removeCallbacks(it) }
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e("RecorderActivity", "Error stopping recorder", e)
                }
                try {
                    release()
                } catch (e: Exception) {
                    Log.e("RecorderActivity", "Error releasing recorder", e)
                }
            }
            recorder = null
            
            // Clear animations
            findViewById<View>(R.id.recordButton)?.clearAnimation()
            ObjectAnimator.ofArgb(
                window.decorView,
                "backgroundColor",
                0x33FF0000,
                0x00000000
            ).apply {
                duration = 300
                start()
            }
            
            // Don't delete the recording file here anymore, let it be handled by saveRecording or dialog dismissal
        } catch (e: Exception) {
            Log.e("RecorderActivity", "Error in cleanup", e)
        }
    }

    private fun cleanupFiles() {
        // Only delete the file if it wasn't saved successfully
        if (!isRecordingSaved) {
            recordingFile?.delete()
        }
        recordingFile = null
    }
}
