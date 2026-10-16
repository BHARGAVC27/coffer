package com.personal.cardvault.crypto

import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCipherTest {
    // Small Argon2 cost so the test runs fast; production uses KdfParams.DEFAULT.
    private val params = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private val salt = Crypto.randomBytes(VaultCipher.SALT_BYTES)

    private fun key(pw: String) = MasterKey(Kdf.derive(pw.toCharArray(), salt, params), salt, params)

    @Test fun masterKeyIs512Bits() {
        assertEquals(64, key("password").bytes.size)
    }

    @Test fun roundTrip() {
        val data = "4111111111111111|123".toByteArray()
        val blob = VaultCipher.seal(data, key("correct horse"))
        assertArrayEquals(data, VaultCipher.open(blob, key("correct horse")))
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val data = "4111111111111111".toByteArray()
        val blob = VaultCipher.seal(data, key("pw12345678"))
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("4111111111111111"))
    }

    @Test fun freshNoncesEachSeal() {
        val k = key("pw12345678")
        val a = VaultCipher.seal("same".toByteArray(), k)
        val b = VaultCipher.seal("same".toByteArray(), k)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test(expected = WrongKeyException::class)
    fun wrongPasswordFails() {
        val blob = VaultCipher.seal("secret".toByteArray(), key("right password"))
        VaultCipher.open(blob, key("wrong password"))
    }

    @Test(expected = WrongKeyException::class)
    fun tamperedCiphertextFails() {
        val k = key("pw12345678")
        val blob = VaultCipher.seal("secret".toByteArray(), k)
        blob[blob.size - 5] = (blob[blob.size - 5].toInt() xor 1).toByte()
        VaultCipher.open(blob, k)
    }

    @Test(expected = WrongKeyException::class)
    fun tamperedHeaderFails() {
        val k = key("pw12345678")
        val blob = VaultCipher.seal("secret".toByteArray(), k)
        blob[20] = (blob[20].toInt() xor 1).toByte() // inside the salt, which is authenticated
        VaultCipher.open(blob, MasterKey(k.bytes, VaultCipher.readHeader(blob).salt, params))
    }

    @Test fun networkDetectionAndLuhn() {
        assertEquals(CardNetwork.VISA, CardUtils.detectNetwork("4111111111111111"))
        assertEquals(CardNetwork.MASTERCARD, CardUtils.detectNetwork("5500000000000004"))
        assertEquals(CardNetwork.MASTERCARD, CardUtils.detectNetwork("2221000000000009"))
        assertEquals(CardNetwork.AMEX, CardUtils.detectNetwork("378282246310005"))
        assertEquals(CardNetwork.RUPAY, CardUtils.detectNetwork("6521000000000000"))
        assertEquals(CardNetwork.DISCOVER, CardUtils.detectNetwork("6011111111111117"))
        assertTrue(CardUtils.luhnValid("4111111111111111"))
        assertFalse(CardUtils.luhnValid("4111111111111112"))
        assertEquals("3782 822463 10005", CardUtils.format("378282246310005"))
        assertEquals("•••• •••• •••• 1111", CardUtils.masked("4111111111111111"))
    }
}
