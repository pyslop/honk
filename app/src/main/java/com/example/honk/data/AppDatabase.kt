package com.example.honk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CustomSound::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customSoundDao(): CustomSoundDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "honk_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
