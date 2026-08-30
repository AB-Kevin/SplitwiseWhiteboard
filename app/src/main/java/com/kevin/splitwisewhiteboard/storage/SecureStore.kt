package com.kevin.splitwisewhiteboard.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Splitwise session cookie (sensitive — it's a live login
 * credential) in an encrypted prefs file, and the rest of the app's small
 * bits of state (selected group, cached whiteboard text) in a plain prefs
 * file since none of that is a credential.
 */
object SecureStore {
    private const val ENCRYPTED_PREFS_NAME = "splitwise_secure_prefs"
    private const val PLAIN_PREFS_NAME = "splitwise_prefs"

    private const val KEY_COOKIE_HEADER = "cookie_header"

    private const val KEY_GROUP_ID = "group_id"
    private const val KEY_GROUP_NAME = "group_name"
    private const val KEY_LAST_WHITEBOARD = "last_whiteboard_text"
    private const val KEY_LAST_UPDATED_LABEL = "last_updated_label"

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun plainPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCookieHeader(context: Context, cookieHeader: String) {
        encryptedPrefs(context).edit().putString(KEY_COOKIE_HEADER, cookieHeader).apply()
    }

    fun getCookieHeader(context: Context): String? =
        encryptedPrefs(context).getString(KEY_COOKIE_HEADER, null)

    fun clearCookieHeader(context: Context) {
        encryptedPrefs(context).edit().remove(KEY_COOKIE_HEADER).apply()
    }

    fun isLoggedIn(context: Context): Boolean = !getCookieHeader(context).isNullOrBlank()

    fun saveSelectedGroup(context: Context, groupId: Long, groupName: String) {
        plainPrefs(context).edit()
            .putLong(KEY_GROUP_ID, groupId)
            .putString(KEY_GROUP_NAME, groupName)
            .apply()
    }

    fun getSelectedGroupId(context: Context): Long =
        plainPrefs(context).getLong(KEY_GROUP_ID, -1L)

    fun getSelectedGroupName(context: Context): String? =
        plainPrefs(context).getString(KEY_GROUP_NAME, null)

    fun saveLastWhiteboard(context: Context, text: String, updatedLabel: String?) {
        plainPrefs(context).edit()
            .putString(KEY_LAST_WHITEBOARD, text)
            .putString(KEY_LAST_UPDATED_LABEL, updatedLabel)
            .apply()
    }

    fun getLastWhiteboard(context: Context): String? =
        plainPrefs(context).getString(KEY_LAST_WHITEBOARD, null)

    fun getLastUpdatedLabel(context: Context): String? =
        plainPrefs(context).getString(KEY_LAST_UPDATED_LABEL, null)
}
