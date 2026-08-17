// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/** What tapping the scan button should do next. */
enum class CameraAccess { GRANTED, NOT_DETERMINED, DENIED_RETRIABLE, PERMANENTLY_DENIED }

/**
 * The whole permission state machine as one pure function, deliberately split from the three Android
 * calls that supply its inputs (`ContextCompat.checkSelfPermission`, a persisted "have we ever asked"
 * flag, `ActivityCompat.shouldShowRequestPermissionRationale`) — those three need an `Activity` and are
 * therefore Robolectric-only to test, which this repo has never added. This table needs neither, so it
 * is plain-JUnit-testable, matching the corpus-function / thin-UI-glue split every other `core/` file
 * uses (`applyMethodChange`, `shouldToggleSelection`, ...).
 *
 * WHY [hasRequestedBefore] EXISTS: `shouldShowRequestPermissionRationale()` returns false both before
 * the permission has ever been asked for AND after "don't ask again" / a second denial — Android gives
 * no other way to tell those two apart. Without a flag the app cannot distinguish a fresh install's
 * first tap from a permanently-denied one, and would show the wrong dialog on one of them.
 */
fun decideCameraAccess(
    granted: Boolean,
    hasRequestedBefore: Boolean,
    shouldShowRationale: Boolean,
): CameraAccess = when {
    granted -> CameraAccess.GRANTED
    !hasRequestedBefore -> CameraAccess.NOT_DETERMINED
    shouldShowRationale -> CameraAccess.DENIED_RETRIABLE
    else -> CameraAccess.PERMANENTLY_DENIED
}
