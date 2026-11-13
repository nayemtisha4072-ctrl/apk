package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity

@Database(
    entities = [CalculationHistoryEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MultiCalculatorDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: MultiCalculatorDatabase? = null

        fun getInstance(context: Context): MultiCalculatorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MultiCalculatorDatabase::class.java,
                    "multi_calculator.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
