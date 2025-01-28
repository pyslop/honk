package com.example.honk.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSoundDao {
    @Query("SELECT * FROM custom_sounds ORDER BY createdAt DESC")
    fun getAllSounds(): Flow<List<CustomSound>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: CustomSound)

    @Delete
    suspend fun delete(sound: CustomSound)
}
