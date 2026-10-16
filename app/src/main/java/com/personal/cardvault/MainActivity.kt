package com.personal.cardvault

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
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
import java.util.function.Consumer

class MainActivity : FragmentActivity() {
    private val app get() = application as CardVaultApp

    private val recordingCallback = Consumer<Int> { state ->
        app.threats.setScreenRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Remote viewers, screen sharing, casting, recordings and screenshots only ever get a black screen.
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
        // Hide all on-screen text from accessibility services that aren't genuine accessibility tools.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.decorView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }

        setContent {
            CardVaultTheme {
                AppRoot(app.vault, app.threats)
            }
        }

        lifecycleScope.launch {
            app.vault.state.collect { if (it == VaultState.UNLOCKED) app.resetIdleTimer() }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val initial = windowManager.addScreenRecordingCallback(mainExecutor, recordingCallback)
            recordingCallback.accept(initial)
        }
    }

    override fun onResume() {
        super.onResume()
        app.threats.refresh()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            windowManager.removeScreenRecordingCallback(recordingCallback)
        }
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        app.resetIdleTimer()
    }
}
