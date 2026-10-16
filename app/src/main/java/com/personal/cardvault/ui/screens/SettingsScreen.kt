package com.personal.cardvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.personal.cardvault.CardVaultApp
import com.personal.cardvault.data.VaultRepository
import com.personal.cardvault.ui.components.FieldShape
import com.personal.cardvault.ui.components.PasswordField
import com.personal.cardvault.ui.components.SectionLabel
import com.personal.cardvault.ui.theme.VaultColors
import com.personal.cardvault.util.BiometricStatus
import com.personal.cardvault.util.biometricStatus
import com.personal.cardvault.util.findActivity
import com.personal.cardvault.util.isUserCancel
import com.personal.cardvault.util.showBiometricPrompt
import kotlinx.coroutines.launch

private val AutoLockOptions = listOf(
    0L to "Immediately",
    30_000L to "After 30 seconds",
    60_000L to "After 1 minute",
    5 * 60_000L to "After 5 minutes",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vault: VaultRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val bioStatus = remember { biometricStatus(context) }
    val protection = remember { vault.deviceProtection() }

    var biometricOn by remember { mutableStateOf(vault.biometricReady()) }
    var autoLock by remember { mutableLongStateOf(vault.prefs.autoLockMillis) }
    var wipeOn by remember { mutableStateOf(vault.prefs.wipeOnFailures) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showErase by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    fun toggleBiometric(enable: Boolean) {
        if (!enable) {
            vault.disableBiometric()
            biometricOn = false
            return
        }
        val activity = context.findActivity() ?: return
        val cipher = runCatching { vault.biometricEnrollCipher() }.getOrElse {
            toast("Couldn't create a fingerprint key: ${it.message}")
            return
        }
        showBiometricPrompt(
            activity, "Turn on fingerprint unlock", "Confirm your fingerprint", "Cancel", cipher,
            onSuccess = { authed ->
                scope.launch {
                    runCatching { vault.enableBiometric(authed) }
                        .onSuccess { biometricOn = true; toast("Fingerprint unlock is on") }
                        .onFailure { toast("Couldn't enable fingerprint unlock") }
                }
            },
            onError = { code, msg -> if (!isUserCancel(code)) toast(msg) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionLabel("Unlock")
            Group {
                SettingRow(
                    Icons.Outlined.Fingerprint, "Fingerprint unlock",
                    when (bioStatus) {
                        BiometricStatus.READY -> "Uses a hardware key that only opens after a strong biometric check"
                        BiometricStatus.NOT_ENROLLED -> "Add a fingerprint in system settings first"
                        BiometricStatus.UNAVAILABLE -> "Not available on this device"
                    },
                    trailing = {
                        Switch(
                            checked = biometricOn,
                            onCheckedChange = ::toggleBiometric,
                            enabled = bioStatus == BiometricStatus.READY,
                        )
                    },
                )
                SettingRow(
                    Icons.Outlined.Timer, "Auto-lock",
                    AutoLockOptions.firstOrNull { it.first == autoLock }?.second.orEmpty() +
                        " after leaving the app · always after ${CardVaultApp.IDLE_TIMEOUT_MS / 60_000} min idle",
                    onClick = { showAutoLock = true },
                )
                SettingRow(Icons.Outlined.Password, "Change master password", "Re-encrypts the vault with a new key", onClick = { showChangePassword = true })
            }

            SectionLabel("Protection")
            Group {
                SettingRow(
                    Icons.Outlined.WarningAmber, "Erase after ${VaultRepository.MAX_ATTEMPTS} wrong passwords",
                    "Destroys the vault if someone keeps guessing",
                    trailing = {
                        Switch(checked = wipeOn, onCheckedChange = { on ->
                            if (on) showWipeConfirm = true else { vault.prefs.wipeOnFailures = false; wipeOn = false }
                        })
                    },
                )
            }

            SectionLabel("How your data is protected")
            Group {
                InfoRow(Icons.Outlined.WifiOff, "No internet permission", "The app is built without network access. It physically cannot send anything anywhere.")
                InfoRow(Icons.Outlined.Key, "Argon2id key derivation", "Your password is stretched with 64 MiB of memory-hard hashing into a 512-bit master key.")
                InfoRow(Icons.Outlined.Shield, "Two ciphers, two keys", "Cards are encrypted with AES-256-GCM, then again with ChaCha20-Poly1305, using independent 256-bit keys.")
                if (protection.startsWith("Software") || protection == "Unknown") {
                    InfoRow(Icons.Outlined.Memory, "Device-bound key", "A third layer uses a key in the Android Keystore. This device reports no secure hardware, so that layer is software-only here.")
                } else {
                    InfoRow(Icons.Outlined.Memory, "Bound to this phone's hardware", "A third layer uses a non-exportable key in the $protection. A copied vault file is useless elsewhere.")
                }
                InfoRow(Icons.Outlined.ScreenLockPortrait, "Screen protected", "Screenshots, screen recording and the recent-apps preview are blocked. Overlays are hidden.")
                InfoRow(Icons.Outlined.PhonelinkLock, "No backups, no leftovers", "Excluded from cloud and device-to-device backup. Copied values clear from the clipboard after 30 s.")
            }

            SectionLabel("Danger zone")
            Group {
                SettingRow(
                    Icons.Outlined.DeleteForever, "Erase vault", "Permanently delete every card and key",
                    tint = VaultColors.Danger, onClick = { showErase = true },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAutoLock) {
        AlertDialog(
            onDismissRequest = { showAutoLock = false },
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text("Lock when I leave the app") },
            text = {
                Column {
                    AutoLockOptions.forEach { (ms, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = autoLock == ms, role = Role.RadioButton) {
                                    autoLock = ms
                                    vault.prefs.autoLockMillis = ms
                                    showAutoLock = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = autoLock == ms, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAutoLock = false }) { Text("Done") } },
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            icon = { Icon(Icons.Outlined.WarningAmber, null, tint = VaultColors.Warning) },
            title = { Text("Turn on auto-erase?") },
            text = { Text("After ${VaultRepository.MAX_ATTEMPTS} wrong passwords in a row, every card is destroyed with no way back. A mistyped password counts too.") },
            confirmButton = {
                TextButton(onClick = {
                    vault.prefs.wipeOnFailures = true
                    wipeOn = true
                    showWipeConfirm = false
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            vault = vault,
            onDismiss = { showChangePassword = false },
            onChanged = {
                showChangePassword = false
                biometricOn = false
                toast("Password changed. Turn fingerprint unlock back on if you use it.")
            },
        )
    }

    if (showErase) {
        EraseDialog(
            onDismiss = { showErase = false },
            onConfirm = {
                showErase = false
                scope.launch { vault.wipeAll() }
            },
        )
    }
}

@Composable
private fun ChangePasswordDialog(vault: VaultRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = current.isNotEmpty() && new.length >= 8 && new == confirm

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Password, null) },
        title = { Text("Change master password") },
        text = {
            Column {
                PasswordField(current, { current = it; error = null }, "Current password", imeAction = ImeAction.Next, enabled = !busy)
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    new, { new = it }, "New password", imeAction = ImeAction.Next, enabled = !busy,
                    supportingText = if (new.isNotEmpty() && new.length < 8) "At least 8 characters" else null,
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    confirm, { confirm = it }, "Confirm new password", enabled = !busy,
                    isError = confirm.isNotEmpty() && confirm != new,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    busy = true
                    val cur = current.toCharArray()
                    val nw = new.toCharArray()
                    scope.launch {
                        val ok = vault.changePassword(cur, nw)
                        busy = false
                        if (ok) onChanged() else { error = "Current password is wrong"; current = "" }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Change")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun EraseDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, null, tint = VaultColors.Danger) },
        title = { Text("Erase everything?") },
        text = {
            Column {
                Text("All cards, keys and settings are destroyed. This cannot be undone. Type ERASE to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    typed, { typed = it }, singleLine = true, shape = FieldShape,
                    placeholder = { Text("ERASE") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "ERASE") {
                Text("Erase", color = if (typed.trim() == "ERASE") VaultColors.Danger else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == VaultColors.Danger) tint else Color.Unspecified)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Icon(icon, null, tint = VaultColors.Success, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
