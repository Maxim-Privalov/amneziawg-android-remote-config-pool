package com.remotepool.secrets

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException

private val Context.secretsDataStore by preferencesDataStore(name = "secret_prefs")

class SecretsManager private constructor(context: Context) {

    private val dataStore = context.applicationContext.secretsDataStore
    private val cipher = SecretCipher(context)

    suspend fun saveSecret(key: String, value: String) = withContext(Dispatchers.IO) {
        val encrypted = cipher.encrypt(value, aad = key)
        dataStore.edit { it[stringPreferencesKey(key)] = encrypted }
        Unit
    }

    suspend fun getSecret(key: String): String? = withContext(Dispatchers.IO) {
        val encrypted = dataStore.data.first()[stringPreferencesKey(key)]
            ?: return@withContext null
        try {
            cipher.decrypt(encrypted, aad = key)
        } catch (e: GeneralSecurityException) {
            null // данные повреждены или ключ в Keystore утерян
        }
    }

    suspend fun hasSecret(key: String): Boolean =
        dataStore.data.first().contains(stringPreferencesKey(key))

    suspend fun removeSecret(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    companion object {
        @Volatile
        private var instance: SecretsManager? = null

        fun get(context: Context): SecretsManager =
            instance ?: synchronized(this) {
                instance ?: SecretsManager(context).also { instance = it }
            }
    }
}