package com.remotepool.secrets

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/** String encryption via Tink AEAD (AES-256-GCM). The key is protected by the Android Keystore. */
class SecretCipher(context: Context) {

    private val appContext = context.applicationContext

    // lazy: initialization accesses the Keystore, so call only from an IO thread
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    /** [aad] binds the ciphertext to the secret name: the value cannot be replaced with another key. */
    fun encrypt(plain: String, aad: String): String {
        val cipherBytes = aead.encrypt(plain.toByteArray(Charsets.UTF_8), aad.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /** Throws GeneralSecurityException if the data is corrupted or the key is lost. */
    fun decrypt(encoded: String, aad: String): String {
        val plainBytes = aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), aad.toByteArray(Charsets.UTF_8))
        return String(plainBytes, Charsets.UTF_8)
    }

    private companion object {
        const val KEYSET_NAME = "secret_keyset"
        const val KEYSET_PREFS = "secret_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://secret_master_key"
    }
}