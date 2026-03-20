package com.localcloud.photosclient.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocalMedia::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_media ADD COLUMN progress INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_local_media_uploadStatus ON local_media (uploadStatus)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_local_media_dateAdded ON local_media (dateAdded)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_local_media_path ON local_media (path)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_media ADD COLUMN remoteId TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add columns with default values
                database.execSQL("ALTER TABLE local_media ADD COLUMN syncState TEXT NOT NULL DEFAULT 'NOT_SYNCED'")
                database.execSQL("ALTER TABLE local_media ADD COLUMN localAvailability TEXT NOT NULL DEFAULT 'LOCAL_AVAILABLE'")
                
                // Update existing successful uploads to SYNCED state
                database.execSQL("UPDATE local_media SET syncState = 'SYNCED' WHERE uploadStatus = 'SUCCESS'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photos_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
