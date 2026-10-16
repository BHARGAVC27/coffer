package com.personal.cardvault.crypto

import com.personal.cardvault.util.CardNetwork
import com.personal.cardvault.util.CardUtils
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher

class VaultCipherTest {
    // Small Argon2 cost so the test runs fast; production uses KdfParams.DEFAULT.
    private val params = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private val salt = Crypto.randomBytes(VaultCipher.SALT_BYTES)

    private fun key(pw: String) = MasterKey(Kdf.derive(pw.toCharArray(), salt, params), salt, params)

    @Test fun keyIsAes256() {
        assertEquals(32, key("password").bytes.size)
    }

    @Test fun roundTrip() {
        val data = "4111111111111111|123".toByteArray()
        val blob = VaultCipher.seal(data, key("correct horse"))
        assertEquals(VaultCipher.VERSION, VaultCipher.readHeader(blob).version)
        assertArrayEquals(data, VaultCipher.open(blob, key("correct horse")))
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val blob = VaultCipher.seal("4111111111111111".toByteArray(), key("pw12345678"))
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("4111111111111111"))
    }

    @Test fun freshNonceEachSeal() {
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
        VaultCipher.open(blob, k)
    }

    @Test fun legacyV1VaultIsReadable() {
        val password = "old-format-pw".toCharArray()
        val data = "legacy cards".toByteArray()
        val blob = sealV1(data, password.copyOf())
        val header = VaultCipher.readHeader(blob)
        assertEquals(LegacyVaultV1.VERSION, header.version)
        assertArrayEquals(data, LegacyVaultV1.open(blob, password, header))
    }

    @Test(expected = WrongKeyException::class)
    fun legacyV1WrongPasswordFails() {
        val blob = sealV1("x".toByteArray(), "right".toCharArray())
        LegacyVaultV1.open(blob, "wrong".toCharArray(), VaultCipher.readHeader(blob))
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

    /** Reproduces the 1.0 file format so the upgrade path stays tested. */
    private fun sealV1(plain: ByteArray, password: CharArray): ByteArray {
        val master = Kdf.derive(password, salt, params, LegacyVaultV1.KEY_BYTES)
        fun sub(info: String) = ByteArray(32).also {
            HKDFBytesGenerator(SHA512Digest()).apply { init(HKDFParameters(master, null, info.toByteArray())) }
                .generateBytes(it, 0, 32)
        }
        val aesNonce = Crypto.randomBytes(12)
        val outerNonce = Crypto.randomBytes(12)
        val header = ByteBuffer.allocate(4 + 1 + 12 + 1 + salt.size + 24)
            .put("CVLT".toByteArray()).put(1)
            .putInt(params.memoryKiB).putInt(params.iterations).putInt(params.parallelism)
            .put(salt.size.toByte()).put(salt).put(aesNonce).put(outerNonce)
            .array()
        val inner = VaultCipher.aesGcm(Cipher.ENCRYPT_MODE, sub("cardvault/v1/aes-256-gcm"), aesNonce, header, plain)
        val outer = ChaCha20Poly1305().apply {
            init(true, AEADParameters(KeyParameter(sub("cardvault/v1/chacha20-poly1305")), 128, outerNonce, header))
        }
        val out = ByteArray(outer.getOutputSize(inner.size))
        val len = outer.processBytes(inner, 0, inner.size, out, 0).let { it + outer.doFinal(out, it) }
        return header + out.copyOf(len)
    }
}
