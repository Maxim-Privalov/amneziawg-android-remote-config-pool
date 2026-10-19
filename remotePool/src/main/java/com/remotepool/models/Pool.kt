package com.remotepool.models

import android.annotation.SuppressLint
import com.remotepool.secrets.SecretsManager
import java.time.Instant
import java.time.Duration

data class Pool(
    val poolName: String,
    val remoteUser: AuthUser,
    val tokenExpiredHours: Int,
) {
    private var lastTokenCreated: Instant? = null;

    suspend fun setToken(password: String, secretsManager : SecretsManager) {
        secretsManager.saveSecret(poolName + "_token", password)
        lastTokenCreated = Instant.now()
    }

    @SuppressLint("NewApi")
    fun checkTokenExpired() : Boolean {
        val created = lastTokenCreated ?: return true

        return Duration.between(created, Instant.now()) > Duration.ofHours(tokenExpiredHours.toLong())
    }

    suspend fun revealToken(secretsManager : SecretsManager) : String? = secretsManager.getSecret(poolName)
}