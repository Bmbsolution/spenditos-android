package com.spenditos.android.core.network

import com.spenditos.android.R
import com.spenditos.android.core.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Runs a Retrofit call that returns an envelope and unwraps `data`. */
suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T {
    val envelope = mapErrors { block() }
    return envelope.data ?: throw ApiError.NoData
}

/** Runs a Retrofit call whose body is ignored (204s, `{message}`-only replies). */
suspend fun apiCallUnit(block: suspend () -> Unit) {
    mapErrors { block() }
}

private suspend fun <R> mapErrors(block: suspend () -> R): R = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ApiError) {
    throw e
} catch (e: HttpException) {
    // A 401 here means the interceptor already refreshed and retried once
    // (see AuthInterceptor); callers classify it, the session stays intact.
    val body = e.response()?.errorBody()?.string()
    throw ApiError.Server(e.code(), serverMessage(body), quotaType(body))
} catch (e: AuthRequiredException) {
    throw ApiError.Unauthorized
} catch (e: KeystoreUnavailableException) {
    throw ApiError.KeystoreUnavailable(e)
} catch (e: IOException) {
    throw ApiError.Network(e)
} catch (e: SerializationException) {
    throw ApiError.Decoding(e)
} catch (e: IllegalArgumentException) {
    // kotlinx.serialization reports some shape mismatches as IllegalArgumentException.
    throw ApiError.Decoding(e)
}

/** `error ?: message` from an error envelope, as iOS `APIClient.serverMessage`. */
/** Shown when an error response carries no message; callers that treat it as "nothing useful to show" compare against this. */
internal fun unknownServerMessage(): String = Strings.get(R.string.data_export_unknown_error)

internal fun serverMessage(body: String?): String {
    if (body.isNullOrBlank()) return unknownServerMessage()
    return try {
        val envelope = SpenditosJson.decodeFromString(ApiEnvelope.serializer(kotlinx.serialization.json.JsonElement.serializer()), body)
        envelope.error ?: envelope.message ?: unknownServerMessage()
    } catch (_: Exception) {
        unknownServerMessage()
    }
}

/** `quotaType` of a flat 403 `QuotaExhausted` body (`{"error":"QuotaExhausted","quotaType":"sentenceEntries",...}`). */
internal fun quotaType(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        (SpenditosJson.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)
            ?.get("quotaType")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content
    } catch (_: Exception) {
        null
    }
}
