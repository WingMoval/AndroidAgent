package com.androidagent.app.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The TopicSession durable database.
 *
 * Frozen facts:
 *  - DATABASE_NAME = "topic_sessions.db" (app-private database directory —
 *    never external/shared storage).
 *  - DATABASE_VERSION = 1 (no migration yet; the builder deliberately does
 *    NOT call fallbackToDestructiveMigration() — future schema changes must
 *    ship explicit Migrations).
 *  - Journal mode = WAL, EXPLICITLY configured via
 *    [WalOpenHelperFactory] (NOT left to Room defaults). The runtime state is
 *    verified by the integration tests reading `PRAGMA journal_mode` back.
 *
 * Tables (frozen: 2 only): topic_sessions, messages.
 */
@Database(
    entities = [TopicSessionEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TopicSessionDatabase : RoomDatabase() {
    abstract fun topicSessionDao(): TopicSessionDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val DATABASE_NAME = "topic_sessions.db"

        /**
         * AG.SESSION-MANAGEMENT-UX-0: migration 1 → 2.
         *
         * Adds the `title_origin` column (the durable title-ownership seam) to
         * existing `topic_sessions` rows. Existing rows get NULL (no title yet /
         * legacy — the legacy normalization pass, if any, runs separately and
         * only touches sessions whose title is deterministically a legacy
         * auto-generated value; in practice existing rows have title = NULL, so
         * no user custom name can be overwritten). This is a pure ADD COLUMN —
         * no data is rewritten, so existing titles (all NULL) are preserved.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE topic_sessions ADD COLUMN title_origin INTEGER",
                )
            }
        }

        /**
         * Opens (or creates) the database for the application context.
         *
         * Uses applicationContext only (no leaked Activity/Service context).
         * Room caches the instance per name/configuration, so repeated calls
         * are thread-safe and cheap.
         *
         * Explicit WAL: [WalOpenHelperFactory] sets
         * `PRAGMA journal_mode = WAL` at database open — the code-level
         * explicit configuration the frozen contract requires (not a comment
         * asserting "Room defaults to WAL").
         */
        fun open(context: Context): TopicSessionDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder(
                appContext,
                TopicSessionDatabase::class.java,
                DATABASE_NAME,
            )
                .openHelperFactory(WalOpenHelperFactory())
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

/**
 * Explicit WAL open-helper factory.
 *
 * Delegates to the standard framework factory and enables write-ahead
 * logging on the openable. PRAGMA journal_mode = WAL is persistent per
 * database file, so it takes effect once at creation and survives reopen.
 */
class WalOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
        val openHelper = delegate.create(configuration)
        // Explicit, code-level WAL (the frozen contract forbids relying on
        // the Room default without an explicit configuration point).
        openHelper.setWriteAheadLoggingEnabled(true)
        return openHelper
    }
}

/**
 * Reads the ACTUAL runtime journal mode of an open database.
 *
 * Used by the integration tests to prove WAL is active at runtime (the
 * assertion targets the SQLite state, not the builder code path).
 */
internal suspend fun journalModeOf(db: TopicSessionDatabase): String =
    withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA journal_mode;").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
        }
    }
