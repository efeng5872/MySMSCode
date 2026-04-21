package com.example.mysmscode

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mysmscode.data.AppDatabase
import com.example.mysmscode.data.KeystoreWebhookCipher
import com.example.mysmscode.data.RoomProcessingRepository
import com.example.mysmscode.data.RoomRobotEndpointRepository
import com.example.mysmscode.data.RoomSenderRuleRepository
import com.example.mysmscode.data.RoomSettingsRepository

class MySmsCodeApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `monitoring_state` (
                    `id` INTEGER NOT NULL,
                    `monitoring_enabled` INTEGER NOT NULL,
                    `stopped_by_user` INTEGER NOT NULL,
                    `last_monitoring_started_at` INTEGER,
                    `last_monitoring_stopped_at` INTEGER,
                    `last_recovery_started_at` INTEGER,
                    `last_recovery_trigger` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE sender_rules
                ADD COLUMN sender_match_mode TEXT NOT NULL DEFAULT 'LEGACY_COMPAT'
                """.trimIndent()
            )
        }
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "mysmscode.db"
        ).addMigrations(migration4To5, migration5To6)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    val robotRepository: RoomRobotEndpointRepository by lazy {
        RoomRobotEndpointRepository(
            database.robotEndpointDao(),
            KeystoreWebhookCipher(application),
        )
    }

    val senderRuleRepository: RoomSenderRuleRepository by lazy {
        RoomSenderRuleRepository(database, database.senderRuleDao())
    }

    val settingsRepository: RoomSettingsRepository by lazy {
        RoomSettingsRepository(
            database.retryPolicyConfigDao(),
            database.monitoringStateDao(),
        )
    }

    val processingRepository: RoomProcessingRepository by lazy {
        RoomProcessingRepository(database, database.processingDao())
    }
}
