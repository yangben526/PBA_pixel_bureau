package app.pixelbureau

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pixelbureau.data.BureauDatabase
import app.pixelbureau.data.BureauRepository
import app.pixelbureau.data.SettingsStore
import app.pixelbureau.net.ChatClient
import app.pixelbureau.net.McpClient
import app.pixelbureau.sprites.SpriteStore

class PixelBureauApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {
    private val database = Room.databaseBuilder(context, BureauDatabase::class.java, "pixel-bureau.db")
        // Every schema step has a real migration. No destructive fallback: an
        // unknown upgrade must fail loudly rather than wipe the user's chats.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    val repository = BureauRepository(database)
    val settings = SettingsStore(context)
    val sprites = SpriteStore(context)
    val mcp = McpClient()
    val chat = ChatClient(mcp = mcp)
}

/** Keeps the existing bureau, chats, and tasks. Adds the chat-to-star counter. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bureau ADD COLUMN chatCredit INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds taskforces (team meetings), their members and their group thread. Existing tables untouched. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // 2→3 already creates the current (steps) schema; only add when missing.
        val has = db.query("PRAGMA table_info(`taskforces`)").use { c ->
            val col = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(col) == "steps") found = true
            found
        }
        if (!has) db.execSQL("ALTER TABLE `taskforces` ADD COLUMN `steps` INTEGER NOT NULL DEFAULT 3")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (sql in TASKFORCE_SCHEMA_SQL) db.execSQL(sql)
    }
}

/** DDL matching the Room entities in data/Records.kt (column order / affinity / nullability). */
internal val TASKFORCE_SCHEMA_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `taskforces` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`objective` TEXT NOT NULL, `status` TEXT NOT NULL, `leadId` TEXT NOT NULL, " +
        "`progress` INTEGER NOT NULL, `checklist` TEXT NOT NULL, `roundsThisRun` INTEGER NOT NULL, " +
        "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `steps` INTEGER NOT NULL DEFAULT 3, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `taskforce_members` (`taskforceId` TEXT NOT NULL, `agentId` TEXT NOT NULL, " +
        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`taskforceId`, `agentId`))",
    "CREATE TABLE IF NOT EXISTS `taskforce_messages` (`id` TEXT NOT NULL, `taskforceId` TEXT NOT NULL, " +
        "`speakerId` TEXT NOT NULL, `speakerName` TEXT NOT NULL, `text` TEXT NOT NULL, `quoteId` TEXT, " +
        "`quoteSpeaker` TEXT, `quoteText` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
)
