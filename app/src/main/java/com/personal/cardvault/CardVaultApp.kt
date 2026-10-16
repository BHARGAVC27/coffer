package com.personal.cardvault

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.personal.cardvault.data.VaultRepository

class CardVaultApp : Application() {
    lateinit var vault: VaultRepository
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val lockTask = Runnable { vault.lock() }
    private var stoppedAt = 0L

    override fun onCreate() {
        super.onCreate()
        vault = VaultRepository(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacks(lockTask)
                stoppedAt = SystemClock.elapsedRealtime()
                val grace = vault.prefs.autoLockMillis
                if (grace <= 0) vault.lock() else handler.postDelayed(lockTask, grace)
            }

            override fun onStart(owner: LifecycleOwner) {
                handler.removeCallbacks(lockTask)
                // Backstop: the posted task may not have run if the process was frozen.
                if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= vault.prefs.autoLockMillis) {
                    vault.lock()
                }
                stoppedAt = 0L
                resetIdleTimer()
            }
        })
    }

    /** Locks after [IDLE_TIMEOUT_MS] without any touch, even while the app is on screen. */
    fun resetIdleTimer() {
        handler.removeCallbacks(lockTask)
        if (vault.isUnlocked) handler.postDelayed(lockTask, IDLE_TIMEOUT_MS)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60_000L
    }
}
