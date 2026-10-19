package com.remotepool.models
import com.google.gson.Gson
import com.remotepool.secrets.SecretsManager

data class AuthUser(
    val username: String,
    val accountId: String,
    val projectName: String
) {
    fun toJson(): String = Gson().toJson(this)

    suspend fun setPassword(password: String, secretsManager : SecretsManager) = secretsManager.saveSecret(this.toJson(), password)

    suspend fun revealPassword(secretsManager: SecretsManager) = secretsManager.getSecret(this.toJson())

    companion object {
        fun fromJson(json: String): AuthUser = Gson().fromJson(json, AuthUser::class.java)
    }
}