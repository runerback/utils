package com.runerback.ntfyclient.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val hasToken: Boolean
        get() = !sharedPreferences.getString(TOKEN_KEY, null).isNullOrBlank()

    fun getToken(): String? = sharedPreferences.getString(TOKEN_KEY, null)

    fun setToken(token: String) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
    }

    fun clearToken() {
        sharedPreferences.edit().remove(TOKEN_KEY).apply()
    }

    private companion object {
        const val FILE_NAME = "token_prefs"
        const val TOKEN_KEY = "ntfy_token"
    }
}
