package com.personal.cardvault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.cardvault.data.Card
import com.personal.cardvault.data.CardKind
import com.personal.cardvault.ui.components.CardNumberTransformation
import com.personal.cardvault.ui.components.CardThemes
import com.personal.cardvault.ui.components.CardVisual
import com.personal.cardvault.ui.components.ExpiryTransformation
import com.personal.cardvault.ui.components.FieldShape
import com.personal.cardvault.ui.components.PasswordField
import com.personal.cardvault.ui.components.SectionLabel
import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(existing: Card?, saving: Boolean, onClose: () -> Unit, onSave: (Card) -> Unit) {
    val initial = remember { existing ?: Card(colorIndex = (0 until CardThemes.size).random()) }

    var number by remember { mutableStateOf(initial.number) }
    var holder by remember { mutableStateOf(initial.holder) }
    var expiry by remember {
        mutableStateOf(if (initial.hasExpiry) "%02d%02d".format(initial.expMonth, initial.expYear % 100) else "")
    }
    var cvv by remember { mutableStateOf(initial.cvv) }
    var pin by remember { mutableStateOf(initial.pin) }
    var kind by remember { mutableStateOf(initial.kind) }
    var issuer by remember { mutableStateOf(initial.issuer) }
    var nickname by remember { mutableStateOf(initial.nickname) }
    var colorIndex by remember { mutableIntStateOf(initial.colorIndex) }
    var notes by remember { mutableStateOf(initial.notes) }
    var cvvFocused by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val network = CardUtils.detectNetwork(number)
    val month = expiry.take(2).toIntOrNull() ?: 0
    val year = if (expiry.length == 4) 2000 + expiry.substring(2).toInt() else 0

    val numberComplete = number.length >= 12
    val luhnWarning = numberComplete && number.length >= minOf(16, CardUtils.maxLength(network)) &&
        !CardUtils.luhnValid(number)
    val expiryError = when {
        expiry.isEmpty() -> null
        expiry.length >= 2 && month !in 1..12 -> "Month must be 01–12"
        expiry.length in 1..3 -> "Use MM/YY"
        else -> null
    }
    val cvvError = cvv.isNotEmpty() && cvv.length < 3
    val canSave = numberComplete && expiryError == null && !cvvError && !saving

    val draft = initial.copy(
        number = number,
        holder = holder.trim(),
        expMonth = if (expiry.length == 4) month else 0,
        expYear = if (expiry.length == 4) year else 0,
        cvv = cvv,
        pin = pin,
        kind = kind,
        issuer = issuer.trim(),
        nickname = nickname.trim(),
        colorIndex = colorIndex,
        notes = notes.trim(),
    )
    val dirty = draft != initial

    fun close() {
        if (dirty) confirmDiscard = true else onClose()
    }
    fun save() {
        if (canSave) onSave(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add card" else "Edit card") },
                navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, "Close") } },
                actions = {
                    TextButton(onClick = ::save, enabled = canSave) { Text(if (saving) "Saving…" else "Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            CardVisual(draft, revealed = true, flipped = cvvFocused)
            Spacer(Modifier.height(6.dp))

            SectionLabel("Card details")
            OutlinedTextField(
                value = number,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit)
                    number = digits.take(CardUtils.maxLength(CardUtils.detectNetwork(digits)))
                },
                label = { Text("Card number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FieldShape,
                visualTransformation = CardNumberTransformation(CardUtils.groups(network, number.length)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                trailingIcon = {
                    if (network != CardNetwork.UNKNOWN) {
                        Text(
                            network.display, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                },
                supportingText = if (luhnWarning) {
                    { Text("This number fails the checksum — double-check it") }
                } else null,
                isError = luhnWarning,
            )
            OutlinedTextField(
                value = holder,
                onValueChange = { holder = it.take(40) },
                label = { Text("Name on card") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = expiry,
                    onValueChange = { expiry = it.filter(Char::isDigit).take(4) },
                    label = { Text("Expiry") },
                    placeholder = { Text("MM/YY") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = FieldShape,
                    visualTransformation = ExpiryTransformation,
                    isError = expiryError != null,
                    supportingText = expiryError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                )
                PasswordField(
                    value = cvv,
                    onValueChange = { cvv = it.filter(Char::isDigit).take(4) },
                    label = "CVV",
                    numeric = true,
                    imeAction = ImeAction.Next,
                    isError = cvvError,
                    modifier = Modifier.weight(1f).onFocusChanged { cvvFocused = it.isFocused },
                )
            }
            PasswordField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = "PIN (optional)",
                numeric = true,
                imeAction = ImeAction.Next,
                supportingText = "Only if you want it stored. Encrypted like everything else.",
            )

            SectionLabel("Type")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CardKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, CardKind.entries.size),
                    ) { Text(k.label) }
                }
            }

            SectionLabel("Label")
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it.take(40) },
                label = { Text("Bank / issuer") },
                placeholder = { Text("e.g. HDFC Bank") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(30) },
                label = { Text("Nickname (optional)") },
                placeholder = { Text("e.g. Travel card") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false, imeAction = ImeAction.Next,
                ),
            )

            SectionLabel("Card colour")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CardThemes.forEachIndexed { i, theme ->
                    val selected = i == colorIndex
                    Box(
                        Modifier.size(44.dp)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(4.dp)
                            .background(Brush.linearGradient(listOf(theme.start, theme.end)), CircleShape)
                            .clickable { colorIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, theme.name, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            SectionLabel("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(500) },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Billing date, reward points, helpline…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("What you've entered on this screen will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
