package com.example.honk

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val honkButton = findViewById<AppCompatImageButton>(R.id.honkButton)
        honkButton.setOnClickListener {
            playHonk()
        }
    }

    private fun playHonk() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.honk_sound)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}