package com.personal.cardvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.cardvault.data.UnlockResult
import com.personal.cardvault.data.VaultRepository
import com.personal.cardvault.ui.components.APP_NAME
import com.personal.cardvault.ui.components.CofferBrand
import com.personal.cardvault.ui.components.PasswordField
import com.personal.cardvault.ui.theme.VaultColors
import com.personal.cardvault.util.Strength
import com.personal.cardvault.util.findActivity
import com.personal.cardvault.util.isUserCancel
import com.personal.cardvault.util.passwordStrength
import com.personal.cardvault.util.showBiometricPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MIN_PASSWORD = 8

@Composable
fun SetupScreen(vault: VaultRepository) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val strength = passwordStrength(password)
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canCreate = password.length >= MIN_PASSWORD && password == confirm && understood && !busy

    fun create() {
        if (!canCreate) return
        val chars = password.toCharArray()
        password = ""
        confirm = ""
        busy = true
        scope.launch { vault.create(chars) }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CofferBrand(logoSize = 80.dp, tagline = "Private card vault")
        Spacer(Modifier.height(32.dp))
        Text("Create your vault", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a master password. It encrypts every card and never leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        PasswordField(
            password, { password = it }, "Master password",
            imeAction = ImeAction.Next, enabled = !busy,
        )
        StrengthMeter(strength, password.length)
        Spacer(Modifier.height(8.dp))
        PasswordField(
            confirm, { confirm = it }, "Confirm password",
            onImeAction = ::create, isError = mismatch, enabled = !busy,
            supportingText = if (mismatch) "Passwords don't match" else null,
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .toggleable(value = understood, enabled = !busy, role = Role.Checkbox) { understood = it }
                    .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = understood, onCheckedChange = null, enabled = !busy)
                Text(
                    "I understand there is no way to recover this password. If I forget it, my saved cards are gone for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::create, enabled = canCreate,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Generating keys…")
            } else {
                Text("Create vault")
            }
        }
        Spacer(Modifier.height(20.dp))
        OfflineNote()
    }
}

@Composable
private fun StrengthMeter(strength: Strength, length: Int) {
    val color by animateColorAsState(
        when (strength) {
            Strength.EMPTY, Strength.WEAK -> VaultColors.Danger
            Strength.FAIR -> VaultColors.Warning
            Strength.STRONG -> VaultColors.Success
            Strength.EXCELLENT -> VaultColors.Gold
        },
        label = "strengthColor",
    )
    val progress by animateFloatAsState(strength.fraction, label = "strength")
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        val hint = when {
            length == 0 -> "At least $MIN_PASSWORD characters. A long passphrase is best."
            length < MIN_PASSWORD -> "${MIN_PASSWORD - length} more characters needed"
            else -> strength.label
        }
        Text(hint, style = MaterialTheme.typography.labelMedium, color = if (length >= MIN_PASSWORD) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OfflineNote() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "No internet permission · nothing ever leaves this device",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun UnlockScreen(vault: VaultRepository) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutUntil by remember { mutableLongStateOf(vault.prefs.lockoutUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var biometricReady by remember { mutableStateOf(vault.biometricReady()) }

    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val lockedSeconds = ((lockoutUntil - now).coerceAtLeast(0) + 999) / 1000
    val lockedOut = lockedSeconds > 0

    fun handle(result: UnlockResult) {
        when (result) {
            UnlockResult.Success, UnlockResult.Wiped -> error = null
            is UnlockResult.WrongPassword -> {
                lockoutUntil = result.lockedUntil
                error = buildString {
                    append("Wrong password.")
                    result.attemptsLeft?.let { append(" $it attempt${if (it == 1) "" else "s"} left before the vault is erased.") }
                }
            }
            is UnlockResult.LockedOut -> lockoutUntil = result.until
            is UnlockResult.Error -> error = result.message
        }
    }

    fun submit() {
        if (password.isEmpty() || busy || lockedOut) return
        val chars = password.toCharArray()
        password = ""
        busy = true
        error = null
        scope.launch {
            handle(vault.unlock(chars))
            busy = false
        }
    }

    fun biometric() {
        val activity = context.findActivity() ?: return
        val cipher = vault.biometricUnlockCipher()
        if (cipher == null) {
            biometricReady = false
            error = "Fingerprints changed, so fingerprint unlock was turned off. Use your password, then re-enable it in Settings."
            return
        }
        showBiometricPrompt(
            activity, "Unlock $APP_NAME", "Confirm it's you", "Use password", cipher,
            onSuccess = { authed ->
                busy = true
                scope.launch {
                    handle(vault.unlockWithBiometric(authed))
                    busy = false
                    biometricReady = vault.biometricReady()
                }
            },
            onError = { code, msg -> if (!isUserCancel(code)) error = msg },
        )
    }

    // Offer the fingerprint prompt as soon as the screen is actually in front of the user.
    LaunchedEffect(Unit) {
        if (biometricReady) {
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            biometric()
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        CofferBrand(logoSize = 96.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Locked. Enter your master password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        PasswordField(
            password, { password = it; error = null }, "Master password",
            onImeAction = ::submit, enabled = !busy && !lockedOut, isError = error != null,
        )

        AnimatedVisibility(error != null || lockedOut) {
            Text(
                if (lockedOut) "Too many attempts. Try again in ${formatSeconds(lockedSeconds)}." else error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::submit, enabled = password.isNotEmpty() && !busy && !lockedOut,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Decrypting…")
            } else {
                Text("Unlock")
            }
        }
        if (biometricReady) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = ::biometric, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Fingerprint, null)
                Spacer(Modifier.width(10.dp))
                Text("Use fingerprint")
            }
        }
        Spacer(Modifier.height(28.dp))
        OfflineNote()
    }
}

private fun formatSeconds(s: Long) = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "${s}s"
