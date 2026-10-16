package com.personal.cardvault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

/** Tries a StrongBox (dedicated security chip) key first, then falls back to the TEE. */
private fun generateKey(build: (strongBox: Boolean) -> KeyGenParameterSpec): SecretKey {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    return try {
        generator.init(build(true))
        generator.generateKey()
    } catch (e: Exception) {
        generator.init(build(false))
        generator.generateKey()
    }
}

private fun baseSpec(alias: String, strongBox: Boolean) =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .apply { if (strongBox) setIsStrongBoxBacked(true) }

/** Format: ivLen | iv | ciphertext */
private fun pack(iv: ByteArray, ct: ByteArray) = byteArrayOf(iv.size.toByte()) + iv + ct
private fun ivOf(blob: ByteArray) = blob.copyOfRange(1, 1 + (blob[0].toInt() and 0xFF))
private fun bodyOf(blob: ByteArray) = blob.copyOfRange(1 + (blob[0].toInt() and 0xFF), blob.size)

/**
 * Outer layer: a non-exportable AES-256 key that lives inside the phone's secure hardware.
 * Even a full copy of the vault file is useless off this device, so offline password
 * guessing on a PC or GPU rig is impossible.
 */
object DeviceKey {
    private const val ALIAS = "cardvault_device_v1"

    private fun key(): SecretKey =
        (keyStore().getKey(ALIAS, null) as? SecretKey)
            ?: generateKey { strongBox -> baseSpec(ALIAS, strongBox).build() }

    fun wrap(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return pack(cipher.iv, cipher.doFinal(data))
    }

    fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, ivOf(blob)))
        return cipher.doFinal(bodyOf(blob))
    }

    fun delete() = runCatching { keyStore().deleteEntry(ALIAS) }

    /** Human-readable description of where the device key lives. */
    fun protectionLevel(): String = runCatching {
        val key = key()
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox security chip"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Trusted Execution Environment"
                else -> "Software keystore"
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "Secure hardware (TEE)" else "Software keystore"
        }
    }.getOrDefault("Unknown")
}

/**
 * Key that can only be used right after a strong biometric check. It encrypts a copy of the
 * master key so fingerprint unlock works without storing the password. Enrolling a new
 * fingerprint permanently invalidates it.
 */
object BiometricKey {
    private const val ALIAS = "cardvault_biometric_v1"

    fun newEncryptCipher(): Cipher {
        delete()
        val key = generateKey { strongBox ->
            baseSpec(ALIAS, strongBox)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
                .build()
        }
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Returns null if the key is gone or was invalidated by a fingerprint change. */
    fun decryptCipher(blob: ByteArray): Cipher? {
        val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ivOf(blob)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            null
        }
    }

    fun seal(authenticatedCipher: Cipher, secret: ByteArray): ByteArray =
        pack(authenticatedCipher.iv, authenticatedCipher.doFinal(secret))

    fun open(authenticatedCipher: Cipher, blob: ByteArray): ByteArray =
        authenticatedCipher.doFinal(bodyOf(blob))

    fun delete() = runCatching { keyStore().deleteEntry(ALIAS) }
}
