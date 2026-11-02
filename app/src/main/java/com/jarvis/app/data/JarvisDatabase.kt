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

@Entity(tableName = "chat_turns")
data class ChatTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fromUser: Boolean,
    val text: String,
    /** Longer answer from the brain, spoken only when asked for details. */
    val detail: String = "",
    val isError: Boolean = false
)

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(turn: ChatTurn): Long

    @Query("SELECT * FROM (SELECT * FROM chat_turns ORDER BY timestamp DESC LIMIT 100) ORDER BY timestamp ASC")
    fun recent(): Flow<List<ChatTurn>>

    @Query("SELECT * FROM chat_turns WHERE isError = 0 ORDER BY timestamp DESC LIMIT :n")
    suspend fun lastN(n: Int): List<ChatTurn>

    @Query("DELETE FROM chat_turns")
    suspend fun clear()
}

// Later phases add Lead and Message entities here and bump the version.
@Database(entities = [WakeEvent::class, ChatTurn::class], version = 2, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun wakeEventDao(): WakeEventDao
    abstract fun chatDao(): ChatDao

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
