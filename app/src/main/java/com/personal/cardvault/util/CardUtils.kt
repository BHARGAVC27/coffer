package com.personal.cardvault.util

enum class CardNetwork(val display: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    RUPAY("RuPay"),
    DISCOVER("Discover"),
    DINERS("Diners Club"),
    JCB("JCB"),
    UNIONPAY("UnionPay"),
    MAESTRO("Maestro"),
    UNKNOWN(""),
}

object CardUtils {
    fun detectNetwork(number: String): CardNetwork {
        val n = number
        fun prefixIn(len: Int, range: IntRange) = n.length >= len && n.take(len).toInt() in range
        return when {
            n.startsWith("34") || n.startsWith("37") -> CardNetwork.AMEX
            n.startsWith("4") -> CardNetwork.VISA
            prefixIn(2, 51..55) || prefixIn(4, 2221..2720) -> CardNetwork.MASTERCARD
            n.startsWith("6521") || n.startsWith("6522") || n.startsWith("508") ||
                n.startsWith("81") || n.startsWith("82") ||
                (n.startsWith("60") && !n.startsWith("6011")) -> CardNetwork.RUPAY
            n.startsWith("6011") || n.startsWith("65") || prefixIn(3, 644..649) -> CardNetwork.DISCOVER
            n.startsWith("36") || n.startsWith("38") || prefixIn(3, 300..305) -> CardNetwork.DINERS
            prefixIn(4, 3528..3589) -> CardNetwork.JCB
            n.startsWith("62") -> CardNetwork.UNIONPAY
            n.startsWith("50") || prefixIn(2, 56..58) || n.startsWith("63") || n.startsWith("67") -> CardNetwork.MAESTRO
            else -> CardNetwork.UNKNOWN
        }
    }

    fun maxLength(network: CardNetwork) = when (network) {
        CardNetwork.AMEX -> 15
        CardNetwork.DINERS -> 16
        else -> 19
    }

    fun cvvLength(network: CardNetwork) = if (network == CardNetwork.AMEX) 4 else 3

    /** Digit group sizes used for display (e.g. Amex is 4-6-5). */
    fun groups(network: CardNetwork, length: Int): IntArray = when {
        network == CardNetwork.AMEX -> intArrayOf(4, 6, 5)
        network == CardNetwork.DINERS && length == 14 -> intArrayOf(4, 6, 4)
        else -> intArrayOf(4, 4, 4, 4, 3)
    }

    fun luhnValid(number: String): Boolean {
        if (number.length < 12) return false
        var sum = 0
        number.reversed().forEachIndexed { i, ch ->
            var d = ch - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    fun format(number: String, network: CardNetwork = detectNetwork(number)): String {
        val sb = StringBuilder()
        var pos = 0
        for (size in groups(network, number.length)) {
            if (pos >= number.length) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(number, pos, minOf(pos + size, number.length))
            pos += size
        }
        if (pos < number.length) sb.append(' ').append(number, pos, number.length)
        return sb.toString()
    }

    /** Everything except the last four digits replaced by dots, keeping the grouping. */
    fun masked(number: String, network: CardNetwork = detectNetwork(number)): String {
        if (number.isEmpty()) return "•••• •••• •••• ••••"
        val keep = 4
        val hidden = number.mapIndexed { i, c -> if (i < number.length - keep) '•' else c }.joinToString("")
        return format(hidden, network)
    }
}
