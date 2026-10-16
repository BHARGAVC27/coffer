package com.personal.cardvault.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.cardvault.data.Card
import com.personal.cardvault.ui.components.CardVisual
import com.personal.cardvault.ui.theme.VaultColors
import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils
import com.personal.cardvault.util.SecureClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val AUTO_HIDE_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(card: Card, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Re-hide sensitive values automatically.
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(AUTO_HIDE_MS)
            revealed = false
        }
    }

    fun copy(label: String, value: String) {
        SecureClipboard.copy(context, label, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("$label copied · clipboard clears in 30 s")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(card.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CardVisual(card, revealed = revealed, flipped = flipped, onClick = { flipped = !flipped })
            Text(
                "Tap the card to flip it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Spacer(Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { revealed = !revealed },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                Spacer(Modifier.width(10.dp))
                Text(if (revealed) "Hide details" else "Reveal details")
            }
            if (revealed) {
                Text(
                    "Hides again automatically after 30 seconds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            ExpiryStatus(card)

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    val network = card.network
                    DetailRow(
                        "Card number",
                        if (revealed) CardUtils.format(card.number, network) else CardUtils.masked(card.number, network),
                        mono = true,
                        onCopy = card.number.takeIf { it.isNotEmpty() }?.let { { copy("Card number", it) } },
                    )
                    RowDivider()
                    DetailRow(
                        "Name on card", card.holder.ifBlank { "—" },
                        onCopy = card.holder.takeIf { it.isNotBlank() }?.let { { copy("Name", it) } },
                    )
                    RowDivider()
                    DetailRow(
                        "Expiry", if (card.hasExpiry) card.expiryText() else "—", mono = true,
                        onCopy = if (card.hasExpiry) ({ copy("Expiry", card.expiryText()) }) else null,
                    )
                    RowDivider()
                    DetailRow(
                        "CVV", secret(card.cvv, revealed), mono = true,
                        onCopy = card.cvv.takeIf { it.isNotEmpty() }?.let { { copy("CVV", it) } },
                    )
                    if (card.pin.isNotEmpty()) {
                        RowDivider()
                        DetailRow("PIN", secret(card.pin, revealed), mono = true, onCopy = { copy("PIN", card.pin) })
                    }
                    RowDivider()
                    DetailRow(
                        "Type",
                        listOf(card.kind.label, network.display).filter { it.isNotEmpty() }.joinToString(" · "),
                    )
                    if (card.issuer.isNotBlank()) {
                        RowDivider()
                        DetailRow("Bank / issuer", card.issuer)
                    }
                    if (card.notes.isNotBlank()) {
                        RowDivider()
                        DetailRow("Notes", if (revealed) card.notes else "Hidden · reveal to view", multiline = true)
                    }
                }
            }

            if (card.number.length >= 12 && !CardUtils.luhnValid(card.number) && card.network != CardNetwork.UNKNOWN) {
                Text(
                    "Heads-up: this number fails the standard checksum. It may have a typo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultColors.Warning,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = { Text("Delete this card?") },
            text = { Text("“${card.title()}” ending ${card.last4} will be permanently removed from the vault.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun secret(value: String, revealed: Boolean) = when {
    value.isEmpty() -> "—"
    revealed -> value
    else -> "•".repeat(value.length)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    multiline: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (mono) FontFamily.Monospace else null,
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, "Copy $label", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ExpiryStatus(card: Card) {
    if (!card.hasExpiry) return
    val months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.of(card.expYear, card.expMonth))
    val (icon, color, text) = when {
        months < 0 -> Triple(Icons.Outlined.ErrorOutline, VaultColors.Danger, "Expired")
        months == 0L -> Triple(Icons.Outlined.Schedule, VaultColors.Warning, "Expires this month")
        months <= 2 -> Triple(Icons.Outlined.Schedule, VaultColors.Warning, "Expires in $months month${if (months == 1L) "" else "s"}")
        else -> Triple(Icons.Outlined.CheckCircle, VaultColors.Success, "Valid · ${formatRemaining(months)} left")
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatRemaining(months: Long): String {
    val y = months / 12
    val m = months % 12
    return listOfNotNull(
        if (y > 0) "$y yr" else null,
        if (m > 0) "$m mo" else null,
    ).joinToString(" ")
}
