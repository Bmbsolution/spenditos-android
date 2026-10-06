package com.spenditos.android.testing

import com.spenditos.android.core.auth.TokenKey
import com.spenditos.android.core.auth.TokenStore
import com.spenditos.android.core.network.KeystoreUnavailableException
import java.util.Base64

/** Unsigned JWTs with the claims the client reads (the server is the verifier). */
object TestJwt {
    fun token(sub: String = "user-1", exp: Long, email: String = "test@example.test", nonce: String = "a"): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = enc.encodeToString("""{"sub":"$sub","exp":$exp,"email":"$email","n":"$nonce"}""".toByteArray())
        return "$header.$payload.sig"
    }
}

class FakeTokenStore(initial: Map<TokenKey, String> = emptyMap()) : TokenStore {
    val values = initial.toMutableMap()
    var unavailable = false

    override fun read(key: TokenKey): String? {
        if (unavailable) throw KeystoreUnavailableException()
        return values[key]
    }

    override fun write(key: TokenKey, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    companion object {
        const val NOW = 1_800_000_000L

        fun signedIn(userId: String = "user-1", accessExp: Long = NOW + 3600) = FakeTokenStore(
            mapOf(
                TokenKey.ACCESS to TestJwt.token(userId, accessExp, nonce = "access"),
                TokenKey.REFRESH to "refresh-$userId",
                TokenKey.ID to TestJwt.token(userId, NOW + 3600, nonce = "id"),
            ),
        )
    }
}
