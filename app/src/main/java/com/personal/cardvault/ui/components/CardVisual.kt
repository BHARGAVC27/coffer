package com.personal.cardvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cardvault.data.Card
import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils

data class CardTheme(val name: String, val start: Color, val end: Color)

val CardThemes = listOf(
    CardTheme("Midnight", Color(0xFF2B3445), Color(0xFF0F141D)),
    CardTheme("Ocean", Color(0xFF1565A8), Color(0xFF14254A)),
    CardTheme("Emerald", Color(0xFF14877C), Color(0xFF07423A)),
    CardTheme("Ruby", Color(0xFFB0193F), Color(0xFF4C0519)),
    CardTheme("Amethyst", Color(0xFF7A3CE0), Color(0xFF2B0F66)),
    CardTheme("Gold", Color(0xFFC79A3A), Color(0xFF5A4012)),
    CardTheme("Graphite", Color(0xFF5B6270), Color(0xFF1E2229)),
    CardTheme("Sunset", Color(0xFFEE6A1F), Color(0xFF7C2410)),
    CardTheme("Rose", Color(0xFFDB3C82), Color(0xFF7A1640)),
    CardTheme("Sky", Color(0xFF1A9AD6), Color(0xFF0B4566)),
)

fun cardTheme(index: Int) = CardThemes[index.mod(CardThemes.size)]

private val OnCard = Color.White
private val OnCardMuted = Color.White.copy(alpha = 0.68f)

/**
 * A realistic card. [revealed] shows the full number / CVV; [flipped] turns it to the back.
 */
@Composable
fun CardVisual(
    card: Card,
    modifier: Modifier = Modifier,
    revealed: Boolean = false,
    flipped: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(550), label = "flip")
    val density = LocalDensity.current.density
    val theme = cardTheme(card.colorIndex)
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
            .shadow(14.dp, shape, ambientColor = theme.start, spotColor = theme.start)
            .clip(shape)
            .background(Brush.linearGradient(listOf(theme.start, theme.end)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        CardDecor()
        if (rotation <= 90f) {
            CardFront(card, revealed)
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardBack(card, revealed)
            }
        }
    }
}

@Composable
private fun CardDecor() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Color.White.copy(alpha = 0.07f),
            radius = size.width * 0.55f,
            center = Offset(size.width * 1.0f, size.height * -0.05f),
        )
        drawCircle(
            Color.White.copy(alpha = 0.045f),
            radius = size.width * 0.42f,
            center = Offset(size.width * 0.05f, size.height * 1.15f),
        )
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * 0.6f, size.height),
            ),
        )
    }
}

@Composable
private fun CardFront(card: Card, revealed: Boolean) {
    val network = card.network
    val number = if (revealed && card.number.isNotEmpty()) CardUtils.format(card.number, network)
    else CardUtils.masked(card.number, network)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    card.issuer.ifBlank { "Your bank" },
                    color = OnCard, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (card.nickname.isNotBlank()) {
                    Text(
                        card.nickname, color = OnCardMuted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                card.kind.label.uppercase(), color = OnCardMuted, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            EmvChip()
            Spacer(Modifier.width(12.dp))
            ContactlessMark()
        }

        Text(
            number, color = OnCard, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                CardCaption("CARD HOLDER")
                Text(
                    card.holder.uppercase().ifBlank { "YOUR NAME" }, color = OnCard, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.8.sp,
                )
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                CardCaption("VALID THRU")
                Text(card.expiryText(), color = OnCard, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            NetworkLogo(network)
        }
    }
}

@Composable
private fun CardBack(card: Card, revealed: Boolean) {
    val cvvText = when {
        card.cvv.isEmpty() -> "—"
        revealed -> card.cvv
        else -> "•".repeat(card.cvv.length)
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(42.dp).background(Color(0xFF0B0B0C)))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFEDEDEA), Color(0xFFD4D3CD), Color(0xFFEDEDEA))),
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.width(70.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cvvText, color = Color(0xFF111111), fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            CardCaption("CVV")
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Stored encrypted on this device only",
                color = OnCardMuted, fontSize = 10.sp, modifier = Modifier.weight(1f),
            )
            NetworkLogo(card.network)
        }
    }
}

@Composable
private fun CardCaption(text: String) {
    Text(text, color = OnCardMuted, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun EmvChip() {
    Canvas(Modifier.size(width = 42.dp, height = 31.dp)) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFF1D98F), Color(0xFFC09B4E), Color(0xFFE7CD84))),
            cornerRadius = r,
        )
        val line = Color(0xFF6F5320).copy(alpha = 0.55f)
        val w = size.width
        val h = size.height
        val sw = 1.dp.toPx()
        drawLine(line, Offset(0f, h / 3), Offset(w * 0.32f, h / 3), sw)
        drawLine(line, Offset(0f, h * 2 / 3), Offset(w * 0.32f, h * 2 / 3), sw)
        drawLine(line, Offset(w * 0.68f, h / 3), Offset(w, h / 3), sw)
        drawLine(line, Offset(w * 0.68f, h * 2 / 3), Offset(w, h * 2 / 3), sw)
        drawRoundRect(
            line, topLeft = Offset(w * 0.32f, h * 0.18f), size = Size(w * 0.36f, h * 0.64f),
            cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(sw),
        )
    }
}

@Composable
private fun ContactlessMark() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val cy = size.height / 2
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            val r = size.width * f
            drawArc(
                Color.White.copy(alpha = 0.85f), startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(-r * 0.35f, cy - r), size = Size(r * 2, r * 2), style = stroke,
            )
        }
    }
}

@Composable
fun NetworkLogo(network: CardNetwork, modifier: Modifier = Modifier) {
    when (network) {
        CardNetwork.VISA -> Text(
            "VISA", modifier = modifier, color = OnCard, fontSize = 23.sp, fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic, letterSpacing = 1.sp,
        )
        CardNetwork.MASTERCARD -> Canvas(modifier.size(width = 46.dp, height = 28.dp)) {
            val r = size.height / 2
            drawCircle(Color(0xFFEB001B), r, Offset(r, r))
            drawCircle(Color(0xFFF79E1B).copy(alpha = 0.92f), r, Offset(size.width - r, r))
        }
        CardNetwork.AMEX -> Box(
            modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text("AMEX", color = Color(0xFF1F6FB8), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        CardNetwork.RUPAY -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Ru", color = OnCard, fontSize = 19.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text("Pay", color = Color(0xFFF7941D), fontSize = 19.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Canvas(Modifier.size(width = 9.dp, height = 14.dp).padding(start = 2.dp)) {
                drawLine(Color(0xFF2FA84F), Offset(0f, 0f), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFF7941D), Offset(size.width, size.height / 2), Offset(0f, size.height), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        CardNetwork.UNKNOWN -> Unit
        else -> Text(
            network.display.uppercase(), modifier = modifier, color = OnCard, fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
        )
    }
}
