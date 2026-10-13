package app.pixelbureau.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bureau")
data class Bureau(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val headcount: Int,
    val reputation: Int,
    val day: Int,
    /** Finished normal chats toward the next star. A task does not add to this. */
    val chatCredit: Int = 0,
)

@Entity(tableName = "agents")
data class Agent(
    @PrimaryKey val id: String,
    val name: String,
    val personality: String,
    val duties: String,
    val spriteKey: String,
    val deskIndex: Int?,
    val status: String,
    val archived: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val agentId: String,
    val title: String,
    val brief: String,
    val status: String,
    val createdAt: Long,
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String,
    val agentId: String,
    val role: String,
    val text: String,
    val taskId: String?,
    val createdAt: Long,
)

/**
 * A team meeting in the glass conference room. [status] follows
 * [app.pixelbureau.logic.TaskforceStatus]; [roundsThisRun] caps the agent loop
 * between user nudges; [checklist] is newline-separated "[x] item" / "[ ] item".
 */
@Entity(tableName = "taskforces")
data class Taskforce(
    @PrimaryKey val id: String,
    val title: String,
    val objective: String,
    val status: String,
    val leadId: String,
    val progress: Int,
    val checklist: String,
    val roundsThisRun: Int,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Discussion steps (rounds) per run, 1–6, chosen in the meeting dialog. */
    @androidx.room.ColumnInfo(defaultValue = "3") val steps: Int = 3,
)

@Entity(tableName = "taskforce_members", primaryKeys = ["taskforceId", "agentId"])
data class TaskforceMember(
    val taskforceId: String,
    val agentId: String,
    val sortOrder: Int,
)

/** One line in a taskforce group thread. [speakerId] is "user", "system" or an agent id. */
@Entity(tableName = "taskforce_messages")
data class TaskforceMessage(
    @PrimaryKey val id: String,
    val taskforceId: String,
    val speakerId: String,
    val speakerName: String,
    val text: String,
    val quoteId: String?,
    val quoteSpeaker: String?,
    val quoteText: String?,
    val createdAt: Long,
)

@Dao
interface TaskforceDao {
    @Query("SELECT * FROM taskforces ORDER BY createdAt ASC")
    fun observe(): Flow<List<Taskforce>>

    @Query("SELECT * FROM taskforces WHERE id = :id")
    suspend fun get(id: String): Taskforce?

    @Query("SELECT * FROM taskforces")
    suspend fun all(): List<Taskforce>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(taskforce: Taskforce)

    @Query("SELECT * FROM taskforce_members ORDER BY sortOrder ASC")
    fun observeMembers(): Flow<List<TaskforceMember>>

    @Query("SELECT * FROM taskforce_members WHERE taskforceId = :id ORDER BY sortOrder ASC")
    suspend fun members(id: String): List<TaskforceMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<TaskforceMember>)

    @Query("SELECT * FROM taskforce_messages WHERE taskforceId = :id ORDER BY createdAt ASC")
    fun observeMessages(id: String): Flow<List<TaskforceMessage>>

    @Query("SELECT * FROM taskforce_messages WHERE taskforceId = :id ORDER BY createdAt ASC")
    suspend fun messages(id: String): List<TaskforceMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TaskforceMessage)

    @Query("UPDATE taskforce_messages SET text = :text WHERE id = :id")
    suspend fun updateMessageText(id: String, text: String)

    @Query("DELETE FROM taskforce_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

@Dao
interface BureauDao {
    @Query("SELECT * FROM bureau WHERE id = 1")
    fun observe(): Flow<Bureau?>

    @Query("SELECT * FROM bureau WHERE id = 1")
    suspend fun get(): Bureau?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bureau: Bureau)
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY sortOrder ASC")
    fun observe(): Flow<List<Agent>>

    @Query("SELECT * FROM agents ORDER BY sortOrder ASC")
    suspend fun all(): List<Agent>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun get(id: String): Agent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: Agent)

    @Update
    suspend fun update(agent: Agent)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observe(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET status = 'failed' WHERE agentId = :agentId AND status IN ('queued', 'working')")
    suspend fun failOpenForAgent(agentId: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks WHERE agentId = :agentId")
    suspend fun deleteForAgent(agentId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE agentId = :agentId ORDER BY createdAt ASC")
    fun observe(agentId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE agentId = :agentId ORDER BY createdAt ASC")
    suspend fun all(agentId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Query("UPDATE messages SET text = :text WHERE id = :id")
    suspend fun updateText(id: String, text: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE agentId = :agentId")
    suspend fun deleteForAgent(agentId: String)

    @Query("DELETE FROM messages WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}

@Database(
    entities = [
        Bureau::class, Agent::class, Task::class, Message::class,
        Taskforce::class, TaskforceMember::class, TaskforceMessage::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class BureauDatabase : RoomDatabase() {
    abstract fun bureau(): BureauDao
    abstract fun agents(): AgentDao
    abstract fun tasks(): TaskDao
    abstract fun messages(): MessageDao
    abstract fun taskforces(): TaskforceDao
}
