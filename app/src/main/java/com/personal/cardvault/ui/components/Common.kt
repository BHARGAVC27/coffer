package com.personal.cardvault.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val FieldShape = RoundedCornerShape(14.dp)

/** Password input that keyboards treat as a password (no learning, no suggestions). */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    numeric: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = FieldShape,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun IconBadge(
    icon: ImageVector,
    size: Dp = 72.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** Inserts spaces between digit groups while the user types the card number. */
class CardNumberTransformation(private val groups: IntArray) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val breaks = buildSet {
            var acc = 0
            for (g in groups) { acc += g; add(acc) }
        }
        val out = StringBuilder()
        raw.forEachIndexed { i, c ->
            if (i > 0 && i in breaks) out.append(' ')
            out.append(c)
        }
        val formatted = out.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + breaks.count { it in 1 until offset }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceAtMost(formatted.length)).count { it != ' ' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/** Shows raw "MMYY" digits as "MM/YY". */
object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = if (raw.length > 2) raw.substring(0, 2) + "/" + raw.substring(2) else raw
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) =
                (if (offset <= 2) offset else offset - 1).coerceAtMost(raw.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
