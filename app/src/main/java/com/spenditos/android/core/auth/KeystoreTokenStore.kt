package com.spenditos.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.KeystoreUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tokens encrypted with an AES-256-GCM key that never leaves Android Keystore,
 * stored in an app-private SharedPreferences file namespaced per environment.
 * The key does not require user authentication, so WorkManager can sync after a
 * reboot (iOS: `AfterFirstUnlockThisDeviceOnly`). Backup is disabled app-wide.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(
    @ApplicationContext context: Context,
    environment: AppEnvironment,
) : TokenStore {
    private val alias = "spenditos_tokens_${environment.storageNamespace}"
    private val prefs = context.getSharedPreferences(alias, Context.MODE_PRIVATE)

    override fun read(key: TokenKey): String? {
        val stored = prefs.getString(key.name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The key is gone for good (e.g. device restore): the value can never
            // be read again, so it is effectively missing; sign-in is required.
            prefs.edit { remove(key.name) }
            null
        } catch (e: AEADBadTagException) {
            prefs.edit { remove(key.name) }
            null
        } catch (e: Exception) {
            throw KeystoreUnavailableException(e)
        }
    }

    override fun write(key: TokenKey, value: String?) {
        if (value == null) {
            // commit (not apply): a token change must be on disk before we act on it.
            prefs.edit(commit = true) { remove(key.name) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit(commit = true) { putString(key.name, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
