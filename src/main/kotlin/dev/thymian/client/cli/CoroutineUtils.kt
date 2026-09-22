package dev.thymian.client.cli

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Cancels this job and suspends until it has completed, swallowing any exception.
 *
 * Replaces `com.intellij.collaboration.async.cancelAndJoinSilently`, whose [Job] overload was
 * removed in 263 (it survives only for `CoroutineScope`) and which is experimental API from a
 * module this plugin does not depend on. The body mirrors the platform's implementation.
 */
internal suspend fun Job.cancelAndJoinSilently() {
    try {
        cancelAndJoin()
    } catch (_: Exception) {
        // Same as the platform helper: the join is best-effort, the caller is shutting down.
    }
}
