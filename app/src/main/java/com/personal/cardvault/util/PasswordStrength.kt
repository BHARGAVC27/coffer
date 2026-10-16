package com.personal.cardvault.util

import kotlin.math.log2

enum class Strength(val label: String, val fraction: Float) {
    EMPTY("", 0f),
    WEAK("Weak", 0.25f),
    FAIR("Fair", 0.5f),
    STRONG("Strong", 0.75f),
    EXCELLENT("Excellent", 1f),
}

/** Rough entropy estimate: character-pool size × length, discounted for repeated characters. */
fun passwordStrength(password: String): Strength {
    if (password.isEmpty()) return Strength.EMPTY
    var pool = 0
    if (password.any { it.isLowerCase() }) pool += 26
    if (password.any { it.isUpperCase() }) pool += 26
    if (password.any { it.isDigit() }) pool += 10
    if (password.any { !it.isLetterOrDigit() }) pool += 33
    val unique = password.toSet().size
    val effectiveLength = (password.length + unique) / 2.0
    val bits = effectiveLength * log2(pool.coerceAtLeast(2).toDouble())
    return when {
        password.length < 8 || bits < 40 -> Strength.WEAK
        bits < 60 -> Strength.FAIR
        bits < 80 -> Strength.STRONG
        else -> Strength.EXCELLENT
    }
}
