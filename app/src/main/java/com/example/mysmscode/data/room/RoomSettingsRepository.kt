package com.example.mysmscode.data

import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.ProcessExitObservation

class RoomSettingsRepository(
    private val retryPolicyConfigDao: RetryPolicyConfigDao,
    private val monitoringStateDao: MonitoringStateDao,
) {
    suspend fun getRetryPolicyConfig(): RetryPolicyConfig {
        return retryPolicyConfigDao.get()?.toDomain() ?: RetryPolicyConfig.default()
    }

    suspend fun saveRetryPolicyConfig(config: RetryPolicyConfig) {
        retryPolicyConfigDao.save(RetryPolicyConfigEntity.fromDomain(config))
    }

    suspend fun getMonitoringState(): MonitoringPersistenceState {
        return monitoringStateDao.get()?.toDomain() ?: MonitoringPersistenceState()
    }

    suspend fun saveMonitoringState(state: MonitoringPersistenceState) {
        monitoringStateDao.save(MonitoringStateEntity.fromDomain(state))
    }

    suspend fun updateWatchdogCheckAt(checkedAt: Long) {
        if (monitoringStateDao.updateWatchdogCheckAt(checkedAt) == 0) {
            saveMonitoringState(MonitoringPersistenceState(lastWatchdogCheckAt = checkedAt))
        }
    }

    suspend fun recordProcessExitIfNew(observation: ProcessExitObservation): Boolean {
        return monitoringStateDao.recordProcessExitIfNew(
            timestamp = observation.timestamp,
            reason = observation.reason.name,
            description = observation.description,
        )
    }
}
