package com.example.mysmscode.data

import com.example.mysmscode.domain.RetryPolicyConfig

class RoomSettingsRepository(
    private val retryPolicyConfigDao: RetryPolicyConfigDao,
) {
    suspend fun getRetryPolicyConfig(): RetryPolicyConfig {
        return retryPolicyConfigDao.get()?.toDomain() ?: RetryPolicyConfig.default()
    }

    suspend fun saveRetryPolicyConfig(config: RetryPolicyConfig) {
        retryPolicyConfigDao.save(RetryPolicyConfigEntity.fromDomain(config))
    }
}