package com.personal.cardvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val APP_NAME = "Coffer"

private val GoldLight = Color(0xFFF6DC95)
private val GoldMid = Color(0xFFE3B964)
private val GoldDark = Color(0xFFAE7F2C)

/**
 * The Coffer mark on its icon tile: a gold "C" ring with a keyhole. Geometry matches the
 * launcher icon (drawn on a 108-unit grid, mark centred in the middle 66 units).
 */
@Composable
fun CofferLogo(size: Dp = 88.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Canvas(
        modifier
            .size(size)
            .shadow(24.dp, shape, ambientColor = GoldMid, spotColor = GoldMid)
            .clip(shape),
    ) {
        val u = this.size.width / 66f // one grid unit; tile shows the 66-unit safe zone
        fun p(v: Float) = (v - 21f) * u // map 108-grid coordinate to tile
        drawRect(Brush.linearGradient(listOf(Color(0xFF242836), Color(0xFF0B0C10))))
        drawRect(
            Brush.radialGradient(
                listOf(GoldMid.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(p(54f), p(50f)),
                radius = 40f * u,
            ),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.06f),
            cornerRadius = CornerRadius(this.size.width * 0.28f),
            style = Stroke(1.dp.toPx()),
        )

        val gold = Brush.linearGradient(
            listOf(GoldLight, GoldMid, GoldDark),
            start = Offset(p(34f), p(34f)),
            end = Offset(p(74f), p(74f)),
        )
        val r = 20f * u
        drawArc(
            gold,
            startAngle = 40f, sweepAngle = 280f, useCenter = false,
            topLeft = Offset(p(54f) - r, p(54f) - r), size = Size(r * 2, r * 2),
            style = Stroke(width = 8.5f * u, cap = StrokeCap.Round),
        )

        val keyGold = Brush.linearGradient(
            listOf(GoldLight, Color(0xFFC8963E)),
            start = Offset(p(50f), p(46f)), end = Offset(p(58f), p(64f)),
        )
        drawCircle(keyGold, radius = 4.6f * u, center = Offset(p(54f), p(50.5f)))
        val stem = Path().apply {
            moveTo(p(52.1f), p(53.5f))
            lineTo(p(50.6f), p(62.2f))
            quadraticTo(p(50.4f), p(63.4f), p(51.6f), p(63.4f))
            lineTo(p(56.4f), p(63.4f))
            quadraticTo(p(57.6f), p(63.4f), p(57.4f), p(62.2f))
            lineTo(p(55.9f), p(53.5f))
            close()
        }
        drawPath(stem, keyGold)
    }
}

/** Logo tile with the wordmark underneath, used on the lock and setup screens. */
@Composable
fun CofferBrand(modifier: Modifier = Modifier, logoSize: Dp = 88.dp, tagline: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CofferLogo(logoSize)
        Spacer(Modifier.height(20.dp))
        Text(
            APP_NAME.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 8.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (tagline != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                tagline.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
