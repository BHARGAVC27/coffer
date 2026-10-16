package com.personal.cardvault.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.personal.cardvault.data.VaultRepository
import com.personal.cardvault.data.VaultState
import com.personal.cardvault.security.ThreatMonitor
import com.personal.cardvault.ui.screens.CardDetailScreen
import com.personal.cardvault.ui.screens.ProtectedScreen
import com.personal.cardvault.ui.screens.EditCardScreen
import com.personal.cardvault.ui.screens.SettingsScreen
import com.personal.cardvault.ui.screens.SetupScreen
import com.personal.cardvault.ui.screens.UnlockScreen
import com.personal.cardvault.ui.screens.WalletScreen
import kotlinx.coroutines.launch

private sealed interface Route {
    data object Wallet : Route
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
    data object Settings : Route
}

@Composable
fun AppRoot(vault: VaultRepository, monitor: ThreatMonitor) {
    val state by vault.state.collectAsState()
    val threats by monitor.threats.collectAsState()
    if (threats.isNotEmpty()) {
        ProtectedScreen(threats, onCheckAgain = monitor::refresh)
        return
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "vaultState",
        ) { s ->
            when (s) {
                VaultState.NEEDS_SETUP -> SetupScreen(vault)
                VaultState.LOCKED -> UnlockScreen(vault)
                VaultState.UNLOCKED -> UnlockedApp(vault)
            }
        }
    }
}

@Composable
private fun UnlockedApp(vault: VaultRepository) {
    val cards by vault.cards.collectAsState()
    val stack = remember { mutableStateListOf<Route>(Route.Wallet) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }

    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120)) },
        label = "route",
    ) { route ->
        when (route) {
            Route.Wallet -> WalletScreen(
                cards = cards,
                onOpen = { push(Route.Detail(it)) },
                onAdd = { push(Route.Edit(null)) },
                onSettings = { push(Route.Settings) },
                onLock = vault::lock,
            )

            is Route.Detail -> {
                val card = cards.firstOrNull { it.id == route.id }
                if (card == null) {
                    LaunchedEffect(Unit) { pop() }
                } else {
                    CardDetailScreen(
                        card = card,
                        onBack = ::pop,
                        onEdit = { push(Route.Edit(card.id)) },
                        onDelete = {
                            scope.launch {
                                runCatching { vault.delete(card.id) }
                                    .onFailure { Toast.makeText(context, "Couldn't delete card", Toast.LENGTH_SHORT).show() }
                            }
                        },
                    )
                }
            }

            is Route.Edit -> EditCardScreen(
                existing = route.id?.let { id -> cards.firstOrNull { it.id == id } },
                saving = saving,
                onClose = ::pop,
                onSave = { card ->
                    saving = true
                    scope.launch {
                        runCatching { vault.upsert(card) }
                            .onSuccess { pop() }
                            .onFailure { Toast.makeText(context, "Couldn't save card", Toast.LENGTH_SHORT).show() }
                        saving = false
                    }
                },
            )

            Route.Settings -> SettingsScreen(vault, onBack = ::pop)
        }
    }
}
