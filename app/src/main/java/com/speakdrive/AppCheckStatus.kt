package com.speakdrive

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Asks Firebase for an App Check token. Tells "Firebase refuses this install" (e.g. a debug token that
 * is not registered) apart from other reasons the AI cannot connect.
 */
object AppCheckStatus {
    private const val TIMEOUT_MS = 15_000L

    /** Null when Firebase accepted this install, otherwise the reason it did not. */
    suspend fun problem(forceRefresh: Boolean): String? {
        val outcome = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Result<Unit>> { cont ->
                try {
                    Firebase.appCheck.getAppCheckToken(forceRefresh).addOnCompleteListener { task ->
                        cont.resume(
                            if (task.isSuccessful) Result.success(Unit)
                            else Result.failure(task.exception ?: IllegalStateException("unknown error"))
                        )
                    }
                } catch (e: Exception) {
                    cont.resume(Result.failure(e))
                }
            }
        } ?: return "timeout"
        return outcome.exceptionOrNull()?.let { it.message?.takeIf(String::isNotBlank) ?: it::class.simpleName ?: "unknown error" }
    }

    /**
     * Same as [problem], but when the SDK is still backing off from earlier refusals ("Too many
     * attempts", up to 4 hours) it starts over first, so a token registered meanwhile is tried now.
     */
    suspend fun check(): String? {
        val first = problem(forceRefresh = false) ?: return null
        if (!first.contains("too many attempts", ignoreCase = true)) return first
        AppCheckInstaller.resetBackoff()
        return problem(forceRefresh = true)
    }
}
