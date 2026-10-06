package com.spenditos.android.core.auth

/** Keys stored by [TokenStore]; same three tokens as the iOS Keychain entries. */
enum class TokenKey { ACCESS, REFRESH, ID }

/**
 * Secure token persistence. [read] returns null when the value is missing and
 * throws [com.spenditos.android.core.network.KeystoreUnavailableException] when
 * secure storage can't be read right now (that must never clear the session).
 */
interface TokenStore {
    fun read(key: TokenKey): String?
    fun write(key: TokenKey, value: String?)
}
