package com.personal.cardvault.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Argon2id cost parameters. Stored in the vault header so they can be raised later. */
data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /** 64 MiB, 3 passes: roughly 1 s on a modern phone, very expensive to brute-force. */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/** The 512-bit key derived from the master password, plus what is needed to re-derive it. */
class MasterKey(val bytes: ByteArray, val salt: ByteArray, val params: KdfParams) {
    fun wipe() = bytes.fill(0)
}

class WrongKeyException : Exception("Wrong password or corrupted vault")

object Crypto {
    private val random = SecureRandom()
    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }
}

object Kdf {
    const val MASTER_KEY_BYTES = 64 // 512 bits

    fun derive(password: CharArray, salt: ByteArray, p: KdfParams): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(p.memoryKiB)
            .withIterations(p.iterations)
            .withParallelism(p.parallelism)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(MASTER_KEY_BYTES)
        generator.generateBytes(password, out)
        return out
    }

    /** HKDF-SHA512: splits the master key into independent per-cipher keys. */
    fun subKey(master: ByteArray, info: String, length: Int = 32): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        hkdf.init(HKDFParameters(master, null, info.toByteArray(Charsets.UTF_8)))
        return ByteArray(length).also { hkdf.generateBytes(it, 0, length) }
    }
}

/**
 * Password layer of the vault file.
 *
 * Layout: MAGIC | version | memKiB | iterations | parallelism | saltLen | salt | aesNonce | chachaNonce | ciphertext
 *
 * The plaintext is encrypted with AES-256-GCM, and that result is encrypted again with
 * ChaCha20-Poly1305 under a second, independent 256-bit key. Breaking the vault requires
 * breaking both ciphers. The whole header is authenticated by both layers.
 */
object VaultCipher {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val VERSION: Byte = 1
    private const val NONCE = 12
    private const val TAG_BITS = 128
    const val SALT_BYTES = 32

    private const val INFO_AES = "cardvault/v1/aes-256-gcm"
    private const val INFO_CHACHA = "cardvault/v1/chacha20-poly1305"

    class Header(
        val salt: ByteArray,
        val params: KdfParams,
        val aesNonce: ByteArray,
        val chachaNonce: ByteArray,
        val length: Int,
    )

    fun seal(plaintext: ByteArray, key: MasterKey): ByteArray {
        val aesNonce = Crypto.randomBytes(NONCE)
        val chachaNonce = Crypto.randomBytes(NONCE)
        val header = writeHeader(key.salt, key.params, aesNonce, chachaNonce)
        val kAes = Kdf.subKey(key.bytes, INFO_AES)
        val kChaCha = Kdf.subKey(key.bytes, INFO_CHACHA)
        try {
            val inner = aesGcm(Cipher.ENCRYPT_MODE, kAes, aesNonce, header, plaintext)
            val outer = chacha(true, kChaCha, chachaNonce, header, inner)
            inner.fill(0)
            return header + outer
        } finally {
            kAes.fill(0)
            kChaCha.fill(0)
        }
    }

    fun open(blob: ByteArray, key: MasterKey): ByteArray {
        val h = readHeader(blob)
        val aad = blob.copyOfRange(0, h.length)
        val body = blob.copyOfRange(h.length, blob.size)
        val kAes = Kdf.subKey(key.bytes, INFO_AES)
        val kChaCha = Kdf.subKey(key.bytes, INFO_CHACHA)
        try {
            val inner = chacha(false, kChaCha, h.chachaNonce, aad, body)
            try {
                return aesGcm(Cipher.DECRYPT_MODE, kAes, h.aesNonce, aad, inner)
            } finally {
                inner.fill(0)
            }
        } catch (e: InvalidCipherTextException) {
            throw WrongKeyException()
        } catch (e: AEADBadTagException) {
            throw WrongKeyException()
        } finally {
            kAes.fill(0)
            kChaCha.fill(0)
        }
    }

    fun readHeader(blob: ByteArray): Header {
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(4).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "Not a Coffer vault file" }
        val version = buf.get()
        require(version == VERSION) { "Unsupported vault version $version" }
        val params = KdfParams(buf.int, buf.int, buf.int)
        val salt = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
        val aesNonce = ByteArray(NONCE).also { buf.get(it) }
        val chachaNonce = ByteArray(NONCE).also { buf.get(it) }
        return Header(salt, params, aesNonce, chachaNonce, buf.position())
    }

    private fun writeHeader(salt: ByteArray, p: KdfParams, aesNonce: ByteArray, chachaNonce: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + 1 + 12 + 1 + salt.size + NONCE * 2)
            .put(MAGIC).put(VERSION)
            .putInt(p.memoryKiB).putInt(p.iterations).putInt(p.parallelism)
            .put(salt.size.toByte()).put(salt)
            .put(aesNonce).put(chachaNonce)
            .array()

    private fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    private fun chacha(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        var len = cipher.processBytes(input, 0, input.size, out, 0)
        len += cipher.doFinal(out, len)
        return if (len == out.size) out else out.copyOf(len).also { out.fill(0) }
    }
}
