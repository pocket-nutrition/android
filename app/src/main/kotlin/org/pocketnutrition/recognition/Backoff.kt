// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

private const val BASE_DELAY_MS = 3_000L
private const val MAX_DELAY_MS = 30_000L
private const val MAX_SHIFT = 10

/** Retry delay after [attempt] consecutive failures (0 = first): 3 s, doubling, capped at 30 s. */
fun backoffDelayMs(attempt: Int): Long =
    (BASE_DELAY_MS shl attempt.coerceIn(0, MAX_SHIFT)).coerceAtMost(MAX_DELAY_MS)
