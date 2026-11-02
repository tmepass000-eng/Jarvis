package com.jarvis.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wake_events")
data class WakeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val confidence: Float
)

@Dao
interface WakeEventDao {
    @Insert
    suspend fun insert(event: WakeEvent)

    @Query("SELECT * FROM wake_events ORDER BY timestamp DESC LIMIT 10")
    fun lastTen(): Flow<List<WakeEvent>>

    @Query("DELETE FROM wake_events WHERE id NOT IN (SELECT id FROM wake_events ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trim()
}

// Later phases add Lead, Message, ChatTurn entities here and bump the version.
@Database(entities = [WakeEvent::class], version = 1, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun wakeEventDao(): WakeEventDao

    companion object {
        @Volatile private var instance: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, JarvisDatabase::class.java, "jarvis.db")
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
