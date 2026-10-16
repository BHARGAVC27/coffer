package com.personal.cardvault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
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

/** The AES-256 key derived from the master password, plus what is needed to re-derive it. */
class MasterKey(val bytes: ByteArray, val salt: ByteArray, val params: KdfParams) {
    fun wipe() = bytes.fill(0)
}

class WrongKeyException : Exception("Wrong password or corrupted vault")

object Crypto {
    private val random = SecureRandom()
    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }
}

object Kdf {
    const val KEY_BYTES = 32 // AES-256

    fun derive(password: CharArray, salt: ByteArray, p: KdfParams, length: Int = KEY_BYTES): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(p.memoryKiB)
            .withIterations(p.iterations)
            .withParallelism(p.parallelism)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(length)
        generator.generateBytes(password, out)
        return out
    }
}

/**
 * Password layer of the vault file: AES-256-GCM under a key derived from the master password.
 *
 * Layout: MAGIC | version | memKiB | iterations | parallelism | saltLen | salt | nonce | ciphertext+tag
 *
 * The whole header is passed as associated data, so changing any byte of it is detected.
 */
object VaultCipher {
    internal val MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    const val VERSION = 2
    internal const val NONCE = 12
    internal const val TAG_BITS = 128
    const val SALT_BYTES = 32

    class Header(
        val version: Int,
        val salt: ByteArray,
        val params: KdfParams,
        /** Number of header bytes; everything before this is authenticated as AAD. */
        val length: Int,
    )

    fun seal(plaintext: ByteArray, key: MasterKey): ByteArray {
        val nonce = Crypto.randomBytes(NONCE)
        val header = ByteBuffer.allocate(4 + 1 + 12 + 1 + key.salt.size + NONCE)
            .put(MAGIC).put(VERSION.toByte())
            .putInt(key.params.memoryKiB).putInt(key.params.iterations).putInt(key.params.parallelism)
            .put(key.salt.size.toByte()).put(key.salt)
            .put(nonce)
            .array()
        return header + aesGcm(Cipher.ENCRYPT_MODE, key.bytes, nonce, header, plaintext)
    }

    fun open(blob: ByteArray, key: MasterKey): ByteArray {
        val h = readHeader(blob)
        require(h.version == VERSION) { "Unsupported vault version ${h.version}" }
        val nonce = blob.copyOfRange(h.length - NONCE, h.length)
        return try {
            aesGcm(
                Cipher.DECRYPT_MODE, key.bytes, nonce,
                blob.copyOfRange(0, h.length), blob.copyOfRange(h.length, blob.size),
            )
        } catch (e: AEADBadTagException) {
            throw WrongKeyException()
        }
    }

    fun readHeader(blob: ByteArray): Header {
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(4).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "Not a Coffer vault file" }
        val version = buf.get().toInt()
        val params = KdfParams(buf.int, buf.int, buf.int)
        val salt = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
        val nonceBytes = when (version) {
            VERSION -> NONCE
            LegacyVaultV1.VERSION -> LegacyVaultV1.NONCE_BYTES
            else -> throw IllegalArgumentException("Unsupported vault version $version")
        }
        buf.position(buf.position() + nonceBytes)
        return Header(version, salt, params, buf.position())
    }

    internal fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }
}
