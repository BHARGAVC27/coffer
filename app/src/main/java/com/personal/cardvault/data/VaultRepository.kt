package com.personal.cardvault.data

import android.content.Context
import androidx.core.util.AtomicFile
import com.personal.cardvault.crypto.BiometricKey
import com.personal.cardvault.crypto.Crypto
import com.personal.cardvault.crypto.DeviceKey
import com.personal.cardvault.crypto.Kdf
import com.personal.cardvault.crypto.KdfParams
import com.personal.cardvault.crypto.MasterKey
import com.personal.cardvault.crypto.VaultCipher
import com.personal.cardvault.crypto.WrongKeyException
import com.personal.cardvault.util.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher

enum class VaultState { NEEDS_SETUP, LOCKED, UNLOCKED }

sealed interface UnlockResult {
    data object Success : UnlockResult
    /** [attemptsLeft] is null when erase-on-failure is off. */
    data class WrongPassword(val attemptsLeft: Int?, val lockedUntil: Long) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object Wiped : UnlockResult
    data class Error(val message: String) : UnlockResult
}

/**
 * Owns the encrypted vault file and the in-memory unlocked state.
 *
 * File = DeviceKey( ChaCha20-Poly1305( AES-256-GCM( cards JSON ) ) ), keys from Argon2id(password).
 * While locked, no key material or card data is held in memory.
 */
class VaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = appContext.noBackupFilesDir
    private val vaultFile = AtomicFile(File(dir, "vault.bin"))
    private val biometricFile = File(dir, "biometric.bin")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    val prefs = SecurityPrefs(appContext)

    private var masterKey: MasterKey? = null

    private val _state = MutableStateFlow(if (vaultFile.baseFile.exists()) VaultState.LOCKED else VaultState.NEEDS_SETUP)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    val isUnlocked: Boolean get() = _state.value == VaultState.UNLOCKED

    // ---- setup / unlock -------------------------------------------------------------------

    suspend fun create(password: CharArray) = withContext(Dispatchers.Default) {
        try {
            val salt = Crypto.randomBytes(VaultCipher.SALT_BYTES)
            val params = KdfParams.DEFAULT
            val key = MasterKey(Kdf.derive(password, salt, params), salt, params)
            mutex.withLock {
                writeVault(emptyList(), key)
                prefs.resetFailures()
                setUnlockedLocked(key, emptyList())
            }
        } finally {
            password.fill('\u0000')
        }
    }

    fun lockoutRemainingMillis(): Long = (prefs.lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0)

    suspend fun unlock(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        try {
            if (lockoutRemainingMillis() > 0) return@withContext UnlockResult.LockedOut(prefs.lockoutUntil)
            val blob = readSealed()
            val header = VaultCipher.readHeader(blob)

            // Count the attempt *before* checking, so force-killing the app mid-check doesn't help.
            val attempts = prefs.failedAttempts + 1
            prefs.failedAttempts = attempts

            val key = MasterKey(Kdf.derive(password, header.salt, header.params), header.salt, header.params)
            val plain = try {
                VaultCipher.open(blob, key)
            } catch (e: WrongKeyException) {
                key.wipe()
                return@withContext onFailedAttempt(attempts)
            }
            val cards = try { CardCodec.decode(plain) } finally { plain.fill(0) }
            mutex.withLock {
                prefs.resetFailures()
                setUnlockedLocked(key, cards)
            }
            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error(e.message ?: "Could not open the vault")
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun onFailedAttempt(attempts: Int): UnlockResult {
        if (prefs.wipeOnFailures && attempts >= MAX_ATTEMPTS) {
            wipeAll()
            return UnlockResult.Wiped
        }
        var until = 0L
        if (attempts >= LOCKOUT_AFTER) {
            val doublings = (attempts - LOCKOUT_AFTER).coerceAtMost(7)
            val duration = (BASE_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
            until = System.currentTimeMillis() + duration
            prefs.lockoutUntil = until
        }
        return UnlockResult.WrongPassword(
            attemptsLeft = if (prefs.wipeOnFailures) MAX_ATTEMPTS - attempts else null,
            lockedUntil = until,
        )
    }

    private fun setUnlockedLocked(key: MasterKey, cards: List<Card>) {
        masterKey?.takeIf { it !== key }?.wipe()
        masterKey = key
        _cards.value = cards
        _state.value = VaultState.UNLOCKED
    }

    /** Locks immediately for the UI; key bytes are zeroed once any in-flight save finishes. */
    fun lock() {
        if (_state.value != VaultState.UNLOCKED) return
        _state.value = VaultState.LOCKED
        _cards.value = emptyList()
        SecureClipboard.clearNow(appContext)
        scope.launch {
            mutex.withLock {
                if (_state.value != VaultState.UNLOCKED) {
                    masterKey?.wipe()
                    masterKey = null
                }
            }
        }
    }

    // ---- card edits -----------------------------------------------------------------------

    suspend fun upsert(card: Card) = mutate { list ->
        val i = list.indexOfFirst { it.id == card.id }
        if (i >= 0) list.toMutableList().also { it[i] = card } else list + card
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    private suspend fun mutate(change: (List<Card>) -> List<Card>) =
        withContext(NonCancellable + Dispatchers.Default) {
            mutex.withLock {
                val key = masterKey
                check(_state.value == VaultState.UNLOCKED && key != null) { "Vault is locked" }
                val updated = change(_cards.value)
                writeVault(updated, key)
                _cards.value = updated
            }
        }

    // ---- password -------------------------------------------------------------------------

    /** Re-encrypts everything under a new password and fresh salt. Disables fingerprint unlock. */
    suspend fun changePassword(current: CharArray, new: CharArray): Boolean =
        withContext(NonCancellable + Dispatchers.Default) {
            try {
                val old = mutex.withLock { masterKey } ?: return@withContext false
                val check = Kdf.derive(current, old.salt, old.params)
                val matches = MessageDigest.isEqual(check, old.bytes)
                check.fill(0)
                if (!matches) return@withContext false

                val salt = Crypto.randomBytes(VaultCipher.SALT_BYTES)
                val params = KdfParams.DEFAULT
                val newKey = MasterKey(Kdf.derive(new, salt, params), salt, params)
                mutex.withLock {
                    if (masterKey !== old) {
                        newKey.wipe()
                        return@withContext false
                    }
                    writeVault(_cards.value, newKey)
                    masterKey = newKey
                    old.wipe()
                }
                disableBiometric()
                true
            } finally {
                current.fill('\u0000')
                new.fill('\u0000')
            }
        }

    // ---- biometric ------------------------------------------------------------------------

    fun biometricReady(): Boolean = prefs.biometricEnabled && biometricFile.exists()

    /** Cipher to hand to BiometricPrompt when turning fingerprint unlock on. */
    fun biometricEnrollCipher(): Cipher = BiometricKey.newEncryptCipher()

    suspend fun enableBiometric(authenticatedCipher: Cipher) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = masterKey ?: error("Vault is locked")
            writeAtomically(biometricFile, BiometricKey.seal(authenticatedCipher, key.bytes))
            prefs.biometricEnabled = true
        }
    }

    fun disableBiometric() {
        biometricFile.delete()
        BiometricKey.delete()
        prefs.biometricEnabled = false
    }

    /** Null means the fingerprint key was invalidated (e.g. a new fingerprint was enrolled). */
    fun biometricUnlockCipher(): Cipher? {
        val cipher = runCatching { BiometricKey.decryptCipher(biometricFile.readBytes()) }.getOrNull()
        if (cipher == null) disableBiometric()
        return cipher
    }

    suspend fun unlockWithBiometric(authenticatedCipher: Cipher): UnlockResult = withContext(Dispatchers.Default) {
        try {
            val keyBytes = BiometricKey.open(authenticatedCipher, biometricFile.readBytes())
            val blob = readSealed()
            val header = VaultCipher.readHeader(blob)
            val key = MasterKey(keyBytes, header.salt, header.params)
            val plain = try {
                VaultCipher.open(blob, key)
            } catch (e: WrongKeyException) {
                key.wipe()
                disableBiometric()
                return@withContext UnlockResult.Error("Fingerprint unlock is out of date. Use your password.")
            }
            val cards = try { CardCodec.decode(plain) } finally { plain.fill(0) }
            mutex.withLock {
                prefs.resetFailures()
                setUnlockedLocked(key, cards)
            }
            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error(e.message ?: "Fingerprint unlock failed")
        }
    }

    // ---- wipe -----------------------------------------------------------------------------

    /** Permanently destroys the vault, the hardware keys and all settings. */
    suspend fun wipeAll() = withContext(NonCancellable + Dispatchers.Default) {
        mutex.withLock {
            masterKey?.wipe()
            masterKey = null
            vaultFile.delete()
            biometricFile.delete()
            DeviceKey.delete()
            BiometricKey.delete()
            prefs.clear()
            _cards.value = emptyList()
            _state.value = VaultState.NEEDS_SETUP
            SecureClipboard.clearNow(appContext)
        }
    }

    fun deviceProtection(): String = DeviceKey.protectionLevel()

    // ---- file io --------------------------------------------------------------------------

    private fun readSealed(): ByteArray = DeviceKey.unwrap(vaultFile.readFully())

    private fun writeVault(cards: List<Card>, key: MasterKey) {
        val plain = CardCodec.encode(cards)
        try {
            val sealed = VaultCipher.seal(plain, key)
            val out = vaultFile.startWrite()
            try {
                out.write(DeviceKey.wrap(sealed))
                vaultFile.finishWrite(out)
            } catch (e: Exception) {
                vaultFile.failWrite(out)
                throw e
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 10
        const val LOCKOUT_AFTER = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 60 * 60_000L
    }
}
