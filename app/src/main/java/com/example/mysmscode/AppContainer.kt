package com.example.mysmscode

import android.app.Application
import androidx.room.Room
import com.example.mysmscode.data.AppDatabase
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
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "mysmscode.db"
        ).fallbackToDestructiveMigration(false).build()
    }

    val robotRepository: RoomRobotEndpointRepository by lazy {
        RoomRobotEndpointRepository(database.robotEndpointDao())
    }

    val senderRuleRepository: RoomSenderRuleRepository by lazy {
        RoomSenderRuleRepository(database, database.senderRuleDao())
    }

    val settingsRepository: RoomSettingsRepository by lazy {
        RoomSettingsRepository(database.retryPolicyConfigDao())
    }

    val processingRepository: RoomProcessingRepository by lazy {
        RoomProcessingRepository(database, database.processingDao())
    }
}