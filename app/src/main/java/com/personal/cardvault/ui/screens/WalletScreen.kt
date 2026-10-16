package com.personal.cardvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.cardvault.data.Card
import com.personal.cardvault.data.CardKind
import com.personal.cardvault.ui.components.CardVisual
import com.personal.cardvault.ui.components.FieldShape
import com.personal.cardvault.ui.components.CofferLogo
import com.personal.cardvault.ui.components.IconBadge

@Composable
fun WalletScreen(
    cards: List<Card>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
) {
    // Plain remember (not rememberSaveable): nothing typed here is ever written to saved state.
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<CardKind?>(null) }

    val filtered = remember(cards, query, filter) {
        val q = query.trim().lowercase()
        cards
            .filter { filter == null || it.kind == filter }
            .filter {
                q.isEmpty() || it.nickname.lowercase().contains(q) || it.issuer.lowercase().contains(q) ||
                    it.holder.lowercase().contains(q) || it.network.display.lowercase().contains(q) ||
                    it.last4.contains(q)
            }
            .sortedByDescending { it.updatedAt }
    }

    Scaffold(
        floatingActionButton = {
            if (cards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Add card") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { Header(cards.size, onLock, onSettings) }

            if (cards.isEmpty()) {
                item(key = "empty") { EmptyWallet(onAdd) }
            } else {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search name, bank or last 4 digits") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                            }
                        },
                        singleLine = true,
                        shape = FieldShape,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    )
                }
                item(key = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == null, onClick = { filter = null },
                            label = { Text("All · ${cards.size}") },
                        )
                        CardKind.entries.forEach { kind ->
                            val count = cards.count { it.kind == kind }
                            if (count > 0 || kind != CardKind.PREPAID) {
                                FilterChip(
                                    selected = filter == kind,
                                    onClick = { filter = if (filter == kind) null else kind },
                                    label = { Text("${kind.label} · $count") },
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "nomatch") {
                        Text(
                            "No cards match your search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                items(filtered, key = { it.id }) { card ->
                    CardVisual(card, modifier = Modifier.animateItem(), onClick = { onOpen(card.id) })
                }
            }
        }
    }
}

@Composable
private fun Header(count: Int, onLock: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CofferLogo(44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Wallet", style = MaterialTheme.typography.headlineLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (count) {
                        0 -> "Encrypted on device"
                        1 -> "1 card · encrypted"
                        else -> "$count cards · encrypted"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Lock now") }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
    }
}

@Composable
private fun EmptyWallet(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 72.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Outlined.CreditCard, size = 96.dp)
        Spacer(Modifier.height(24.dp))
        Text("Your wallet is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your first card. It's encrypted with your master password and never leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add a card")
        }
    }
}
