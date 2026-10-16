package com.personal.cardvault.data

import android.content.Context

/** Non-secret settings and attempt counters. Card data never goes here. */
class SecurityPrefs(context: Context) {
    private val sp = context.getSharedPreferences("security", Context.MODE_PRIVATE)

    /** How long the app may sit in the background before locking. 0 = immediately. */
    var autoLockMillis: Long
        get() = sp.getLong(KEY_AUTO_LOCK, 0L)
        set(v) { sp.edit().putLong(KEY_AUTO_LOCK, v).apply() }

    var wipeOnFailures: Boolean
        get() = sp.getBoolean(KEY_WIPE, false)
        set(v) { sp.edit().putBoolean(KEY_WIPE, v).apply() }

    var biometricEnabled: Boolean
        get() = sp.getBoolean(KEY_BIO, false)
        set(v) { sp.edit().putBoolean(KEY_BIO, v).apply() }

    // Written with commit() so an attempt is counted before the password is even checked.
    var failedAttempts: Int
        get() = sp.getInt(KEY_FAILS, 0)
        set(v) { sp.edit().putInt(KEY_FAILS, v).commit() }

    var lockoutUntil: Long
        get() = sp.getLong(KEY_LOCKOUT, 0L)
        set(v) { sp.edit().putLong(KEY_LOCKOUT, v).commit() }

    fun resetFailures() {
        sp.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT, 0L).commit()
    }

    fun clear() {
        sp.edit().clear().commit()
    }

    private companion object {
        const val KEY_AUTO_LOCK = "auto_lock_ms"
        const val KEY_WIPE = "wipe_on_failures"
        const val KEY_BIO = "biometric_enabled"
        const val KEY_FAILS = "failed_attempts"
        const val KEY_LOCKOUT = "lockout_until"
    }
}
