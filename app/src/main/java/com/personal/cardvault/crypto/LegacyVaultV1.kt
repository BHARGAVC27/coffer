package com.personal.cardvault.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/**
 * Read-only support for vaults written by version 1.0 of the app, so they can be upgraded to
 * the current format on the next password unlock. Nothing is ever written in this format.
 */
object LegacyVaultV1 {
    const val VERSION = 1
    const val KEY_BYTES = 64
    internal const val NONCE_BYTES = 24 // AES nonce + second-layer nonce

    fun open(blob: ByteArray, password: CharArray, header: VaultCipher.Header): ByteArray {
        val master = Kdf.derive(password, header.salt, header.params, KEY_BYTES)
        val aad = blob.copyOfRange(0, header.length)
        val nonceStart = header.length - NONCE_BYTES
        val aesNonce = blob.copyOfRange(nonceStart, nonceStart + 12)
        val outerNonce = blob.copyOfRange(nonceStart + 12, header.length)
        val body = blob.copyOfRange(header.length, blob.size)
        val kAes = subKey(master, "cardvault/v1/aes-256-gcm")
        val kOuter = subKey(master, "cardvault/v1/chacha20-poly1305")
        master.fill(0)
        try {
            val outer = ChaCha20Poly1305()
            outer.init(false, AEADParameters(KeyParameter(kOuter), 128, outerNonce, aad))
            val inner = ByteArray(outer.getOutputSize(body.size))
            var len = outer.processBytes(body, 0, body.size, inner, 0)
            len += outer.doFinal(inner, len)
            try {
                return VaultCipher.aesGcm(Cipher.DECRYPT_MODE, kAes, aesNonce, aad, inner.copyOf(len))
            } finally {
                inner.fill(0)
            }
        } catch (e: InvalidCipherTextException) {
            throw WrongKeyException()
        } catch (e: AEADBadTagException) {
            throw WrongKeyException()
        } finally {
            kAes.fill(0)
            kOuter.fill(0)
        }
    }

    private fun subKey(master: ByteArray, info: String): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        hkdf.init(HKDFParameters(master, null, info.toByteArray(Charsets.UTF_8)))
        return ByteArray(32).also { hkdf.generateBytes(it, 0, 32) }
    }
}
