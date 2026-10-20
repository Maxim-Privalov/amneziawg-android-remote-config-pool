package com.remotepool.models

import android.annotation.SuppressLint
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.remotepool.secrets.SecretsManager
import java.lang.reflect.Type
import java.time.Instant
import java.time.Duration

data class Pool(
    val poolName: String,
    val remoteUser: AuthUser,
    val tokenExpiredHours: Int,
) {
    private var lastTokenCreated: Instant? = null;

    suspend fun setToken(token: String, secretsManager : SecretsManager) {
        secretsManager.saveSecret(poolName + "_token", token)
        lastTokenCreated = Instant.now()
    }

    @SuppressLint("NewApi")
    fun checkTokenExpired() : Boolean {
        val created = lastTokenCreated ?: return true

        return Duration.between(created, Instant.now()) > Duration.ofHours(tokenExpiredHours.toLong())
    }

    suspend fun revealToken(secretsManager : SecretsManager) : String? = secretsManager.getSecret(poolName + "_token")

    fun withName(newName: String): Pool =
        copy(poolName = newName).also { it.lastTokenCreated = lastTokenCreated }

    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): Pool = gson.fromJson(json, Pool::class.java)

        // Instant сериализуем явно (ISO-8601), а не рефлексией по его внутренним полям.
        @SuppressLint("NewApi")
        private object InstantAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
            override fun serialize(src: Instant, type: Type, ctx: JsonSerializationContext): JsonElement =
                JsonPrimitive(src.toString())

            override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Instant =
                Instant.parse(json.asString)
        }

        private val gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantAdapter)
            .create()
    }
}
