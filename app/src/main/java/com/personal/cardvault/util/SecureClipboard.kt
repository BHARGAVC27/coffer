package com.personal.cardvault.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies sensitive values with the "sensitive" flag (hidden from clipboard previews and
 * keyboard suggestions) and wipes the clipboard again after [CLEAR_AFTER_MS].
 */
object SecureClipboard {
    const val CLEAR_AFTER_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(context: Context, label: String, value: String) {
        val app = context.applicationContext
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(key, true)
        }
        cm.setPrimaryClip(clip)

        pending?.let(handler::removeCallbacks)
        val clear = Runnable { clearNow(app) }
        pending = clear
        handler.postDelayed(clear, CLEAR_AFTER_MS)
    }

    /** Clears the clipboard if we put something on it that hasn't been cleared yet. */
    fun clearNow(context: Context) {
        val task = pending ?: return
        handler.removeCallbacks(task)
        pending = null
        runCatching {
            context.applicationContext.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip()
        }
    }
}
