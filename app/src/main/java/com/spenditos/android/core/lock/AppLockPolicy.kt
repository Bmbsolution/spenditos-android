package com.spenditos.android.core.lock

/**
 * When the app lock engages. Port of iOS `BiometricLockPolicy`, plus a background grace period:
 * Android moves the app to the background for the camera, file picker and permission dialogs,
 * which iOS reports as "inactive" and ignores.
 */
object AppLockPolicy {
    const val DEFAULT_GRACE_MILLIS = 30_000L

    /** Cold launch: only when a saved session was restored (a fresh sign-in this run proved identity). */
    fun shouldLockOnColdLaunch(
        isSignedIn: Boolean,
        restoredAtLaunch: Boolean,
        lockEnabled: Boolean,
        alreadyLocked: Boolean = false,
    ): Boolean = isSignedIn && restoredAtLaunch && lockEnabled && !alreadyLocked

    /** Return to the foreground after [backgroundMillis] away. */
    fun shouldLockOnForeground(
        backgroundMillis: Long?,
        isSignedIn: Boolean,
        lockEnabled: Boolean,
        alreadyLocked: Boolean,
        graceMillis: Long = DEFAULT_GRACE_MILLIS,
    ): Boolean {
        if (backgroundMillis == null) return false
        if (!isSignedIn || !lockEnabled || alreadyLocked) return false
        return backgroundMillis >= graceMillis
    }
}
