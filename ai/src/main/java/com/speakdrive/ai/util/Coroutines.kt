package com.speakdrive.ai.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets cancellation through: wrapping a suspend call in plain
 * runCatching would swallow the CancellationException and keep a cancelled coroutine running.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
