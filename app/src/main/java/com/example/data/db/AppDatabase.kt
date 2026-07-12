package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sender: String, // "user", "rabiya", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String? = null
)

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String, // "Ideas", "Reminders", "Personal", "Cyber Sec"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "lock_settings")
data class LockSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val passcode: String = "", // empty means offline lock disabled
    val isFaceScanEnabled: Boolean = false
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT 30")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("SELECT DISTINCT sessionId FROM chat_messages ORDER BY timestamp DESC")
    fun getAllSessionIds(): Flow<List<String>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<MemoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MemoryItemEntity)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
}

@Dao
interface LockDao {
    @Query("SELECT * FROM lock_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): LockSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: LockSettingsEntity)
}

@Database(
    entities = [ChatMessageEntity::class, MemoryItemEntity::class, LockSettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun lockDao(): LockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rabiya_neural_v1_db"
                )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
