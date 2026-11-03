package com.jarvis.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

object LeadStatus {
    const val NEW = "New"          // found, not yet swiped
    const val FOUND = "Found"      // kept (swiped right); first Pipeline column
    const val SKIPPED = "Skipped"
    const val CONTACTED = "Contacted"
    const val REPLIED = "Replied"
    const val MEETING = "Meeting"
    const val WON = "Won"
    const val LOST = "Lost"
    /** Pipeline columns, in order. */
    val PIPELINE = listOf(FOUND, CONTACTED, REPLIED, MEETING, WON, LOST)
}

@Entity(tableName = "leads", indices = [Index(value = ["profileUrl"], unique = true)])
data class Lead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: String,       // Instagram, LinkedIn or Web
    val profileUrl: String,
    val niche: String,
    val region: String,
    val notes: String = "",
    val score: Int,
    val reason: String,
    val status: String = LeadStatus.NEW,
    val starred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val contactedAt: Long? = null,
    val followUpAt: Long? = null,
)

/** One outreach handoff (copy + open profile), for the per-platform daily warning. */
@Entity(tableName = "outreach")
data class Outreach(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leadId: Long,
    val platform: String,
    val at: Long = System.currentTimeMillis(),
)

@Dao
interface OutreachDao {
    @Insert
    suspend fun insert(o: Outreach)

    @Query("SELECT COUNT(*) FROM outreach WHERE platform = :platform AND at >= :since")
    suspend fun countSince(platform: String, since: Long): Int
}

@Dao
interface LeadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(leads: List<Lead>): List<Long>

    @Update
    suspend fun update(lead: Lead)

    @Query("SELECT * FROM leads WHERE status != 'Skipped' ORDER BY score DESC, createdAt DESC LIMIT 200")
    fun active(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE status = 'New' ORDER BY score DESC, createdAt DESC")
    fun unswiped(): Flow<List<Lead>>

    @Query("SELECT COUNT(*) FROM leads WHERE createdAt >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun byId(id: Long): Lead?

    @Query("SELECT * FROM leads WHERE id = :id")
    fun observe(id: Long): Flow<Lead?>

    @Query("SELECT * FROM leads WHERE status IN ('Found','Contacted','Replied','Meeting','Won','Lost') ORDER BY starred DESC, score DESC")
    fun pipeline(): Flow<List<Lead>>

    @Query("SELECT profileUrl FROM leads")
    suspend fun allUrls(): List<String>

    @Query("DELETE FROM leads")
    suspend fun clear()
}

@Database(entities = [WakeEvent::class, ChatTurn::class, Lead::class, Outreach::class], version = 4, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun wakeEventDao(): WakeEventDao
    abstract fun chatDao(): ChatDao
    abstract fun leadDao(): LeadDao
    abstract fun outreachDao(): OutreachDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `leads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `platform` TEXT NOT NULL, `profileUrl` TEXT NOT NULL, `niche` TEXT NOT NULL, `region` TEXT NOT NULL, `notes` TEXT NOT NULL, `score` INTEGER NOT NULL, `reason` TEXT NOT NULL, `status` TEXT NOT NULL, `starred` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_leads_profileUrl` ON `leads` (`profileUrl`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `leads` ADD COLUMN `contactedAt` INTEGER")
                db.execSQL("ALTER TABLE `leads` ADD COLUMN `followUpAt` INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `outreach` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `leadId` INTEGER NOT NULL, `platform` TEXT NOT NULL, `at` INTEGER NOT NULL)")
            }
        }

        @Volatile private var instance: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, JarvisDatabase::class.java, "jarvis.db")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
