package com.remotepool.models
import com.google.gson.Gson

data class AuthUser(
    val username: String,
    val password: String,
    val accountId: String,
    val projectName: String
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): AuthUser = Gson().fromJson(json, AuthUser::class.java)
    }
}