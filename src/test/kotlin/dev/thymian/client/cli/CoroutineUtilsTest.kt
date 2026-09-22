package dev.thymian.client.cli

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CoroutineUtilsTest {

    @Test
    fun `cancels a running job and suspends until it has completed`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var completedNormally = false
        val job = launch {
            try {
                started.complete(Unit)
                delay(Long.MAX_VALUE.milliseconds)
            } finally {
                completedNormally = true
            }
        }
        started.await()

        job.cancelAndJoinSilently()

        assertTrue(job.isCompleted)
        assertTrue(completedNormally)
    }

    @Test
    fun `swallows an exception thrown by the job instead of propagating it`() = runBlocking {
        val job = launch {
            throw IllegalStateException("boom")
        }

        // Must not throw, even though the job itself failed.
        job.cancelAndJoinSilently()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `is a no-op for an already completed job`() = runBlocking {
        val job = launch { }
        job.join()

        job.cancelAndJoinSilently()

        assertTrue(job.isCompleted)
    }
}
