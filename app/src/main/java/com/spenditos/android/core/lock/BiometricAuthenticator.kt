package com.spenditos.android.core.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class LockAvailability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

sealed interface LockAuthResult {
    data object Success : LockAuthResult
    /** The user dismissed or cancelled the prompt. */
    data object Cancelled : LockAuthResult
    data class Failed(val message: String) : LockAuthResult
}

interface BiometricAuthenticator {
    fun availability(): LockAvailability
    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String?): LockAuthResult
}

/** Fingerprint/face or the device PIN/pattern (the "Use passcode" path of iOS). */
@Singleton
class SystemBiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAuthenticator {
    // BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the combination supported on API 28 and 29 as well.
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun availability(): LockAvailability = when (BiometricManager.from(context).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NOT_ENROLLED
        else -> LockAvailability.UNAVAILABLE
    }

    override suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String?): LockAuthResult =
        suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(LockAuthResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (continuation.isActive) {
                            continuation.resume(if (cancelled) LockAuthResult.Cancelled else LockAuthResult.Failed(errString.toString()))
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { subtitle?.let(::setSubtitle) }
                .setAllowedAuthenticators(authenticators)
                .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }
}
