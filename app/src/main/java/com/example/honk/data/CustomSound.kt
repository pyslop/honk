package com.example.honk.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_sounds")
data class CustomSound(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val filePath: String
)
