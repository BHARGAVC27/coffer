package com.personal.cardvault.data

import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class CardKind(val label: String) { CREDIT("Credit"), DEBIT("Debit"), PREPAID("Prepaid") }

data class Card(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "",
    val holder: String = "",
    /** 1..12, or 0 if not entered. */
    val expMonth: Int = 0,
    /** Four-digit year, or 0 if not entered. */
    val expYear: Int = 0,
    val cvv: String = "",
    val pin: String = "",
    val kind: CardKind = CardKind.CREDIT,
    val issuer: String = "",
    val nickname: String = "",
    val colorIndex: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val network: CardNetwork get() = CardUtils.detectNetwork(number)
    val last4: String get() = number.takeLast(4)
    val hasExpiry: Boolean get() = expMonth in 1..12 && expYear > 0

    fun expiryText(): String =
        if (hasExpiry) "%02d/%02d".format(expMonth, expYear % 100) else "MM/YY"

    fun title(): String = nickname.ifBlank { issuer.ifBlank { network.display.ifBlank { "Card" } } }
}

/** JSON (de)serialisation of the decrypted vault contents. */
object CardCodec {
    private const val PAD_BLOCK = 1024

    fun encode(cards: List<Card>): ByteArray {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id).put("number", c.number).put("holder", c.holder)
                    .put("expMonth", c.expMonth).put("expYear", c.expYear)
                    .put("cvv", c.cvv).put("pin", c.pin).put("kind", c.kind.name)
                    .put("issuer", c.issuer).put("nickname", c.nickname)
                    .put("colorIndex", c.colorIndex).put("notes", c.notes)
                    .put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)
            )
        }
        val json = JSONObject().put("v", 1).put("cards", arr).toString().toByteArray(Charsets.UTF_8)
        // Pad to a fixed block size so the file size doesn't reveal how many cards are stored.
        val padded = ByteArray(((json.size / PAD_BLOCK) + 1) * PAD_BLOCK) { ' '.code.toByte() }
        json.copyInto(padded)
        json.fill(0)
        return padded
    }

    fun decode(bytes: ByteArray): List<Card> {
        val root = JSONObject(String(bytes, Charsets.UTF_8).trimEnd())
        val arr = root.getJSONArray("cards")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Card(
                id = o.getString("id"),
                number = o.optString("number"),
                holder = o.optString("holder"),
                expMonth = o.optInt("expMonth"),
                expYear = o.optInt("expYear"),
                cvv = o.optString("cvv"),
                pin = o.optString("pin"),
                kind = runCatching { CardKind.valueOf(o.optString("kind")) }.getOrDefault(CardKind.CREDIT),
                issuer = o.optString("issuer"),
                nickname = o.optString("nickname"),
                colorIndex = o.optInt("colorIndex"),
                notes = o.optString("notes"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}
