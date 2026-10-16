package com.personal.cardvault

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.personal.cardvault.data.VaultState
import com.personal.cardvault.ui.AppRoot
import com.personal.cardvault.ui.theme.CardVaultTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val app get() = application as CardVaultApp

    override fun onCreate(savedInstanceState: Bundle?) {
        // Block screenshots, screen recording, casting and the recent-apps thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Ignore taps while another app draws over us (tapjacking).
        window.decorView.filterTouchesWhenObscured = true

        setContent {
            CardVaultTheme {
                AppRoot(app.vault)
            }
        }

        lifecycleScope.launch {
            app.vault.state.collect { if (it == VaultState.UNLOCKED) app.resetIdleTimer() }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        app.resetIdleTimer()
    }
}
