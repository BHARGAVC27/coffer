package com.personal.cardvault.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.cardvault.security.Threat
import com.personal.cardvault.security.ThreatKind
import com.personal.cardvault.ui.components.CofferLogo
import com.personal.cardvault.ui.theme.VaultColors

/**
 * Shown instead of everything else while something could be watching the screen.
 * It contains no card data, so even an app that can read it learns nothing.
 */
@Composable
fun ProtectedScreen(threats: List<Threat>, onCheckAgain: () -> Unit) {
    val context = LocalContext.current
    fun open(action: String) = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CofferLogo(72.dp)
        Spacer(Modifier.height(24.dp))
        Text("Coffer is locked for your safety", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Something on this phone could see or control your screen. Your cards stay hidden until it's turned off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        threats.forEach { threat ->
            when (threat.kind) {
                ThreatKind.SCREEN_READING_APP -> ThreatCard(
                    Icons.Outlined.Accessibility,
                    "App with screen access",
                    "${threat.apps.joinToString()} can read and control your screen. Turn it off under Accessibility, or uninstall it if you don't recognise it.",
                    "Open Accessibility settings",
                ) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }

                ThreatKind.DEBUGGING -> ThreatCard(
                    Icons.Outlined.DeveloperMode,
                    "USB or wireless debugging is on",
                    "A connected computer could read your screen. Turn off USB debugging and Wireless debugging in Developer options.",
                    "Open Developer options",
                ) { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }

                ThreatKind.SCREEN_RECORDING -> ThreatCard(
                    Icons.Outlined.Videocam,
                    "Screen is being recorded or shared",
                    "Stop the recording, screen share or cast, then come back.",
                    null,
                ) {}

                ThreatKind.ROOTED -> ThreatCard(
                    Icons.Outlined.GppBad,
                    "This phone is rooted",
                    "Apps with root access can get around any app's protection, so Coffer won't open on a rooted phone.",
                    null,
                ) {}
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCheckAgain) { Text("I've fixed it, check again") }
    }
}

@Composable
private fun ThreatCard(icon: ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Surface(
        color = Color(0xFF141519),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = VaultColors.Danger, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action) }
            }
        }
    }
}
