package dev.thymian.intellijplugin.cli

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import dev.thymian.intellijplugin.settings.ThymianSettingsState
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

@Service(Service.Level.APP)
internal class ThymianCLISessionManager(private val cs: CoroutineScope) : Disposable {
    init {
        Disposer.register(application, this)
    }

    private val lock = Any()

    private var tailClose: CompletableFuture<*> = CompletableFuture.completedFuture(Unit)
    private val pendingRequests = mutableListOf<ThymianCLISession>()

    fun getThymianCLI(): CompletableFuture<ThymianCLI> {
        val settings = ThymianSettingsState.getInstance().state

        val session = ThymianCLIAdapter(settings, cs)
            .let { ThymianCLILocalRunner(it, settings, cs) }
            .let { ThymianCLISession(it) }


        addSession(session)

        val result: CompletableFuture<ThymianCLI> = tailClose.thenApply { removeSession() }
            .thenCompose { session ->
                if (session == null) {
                    return@thenCompose CompletableFuture.failedFuture(IllegalStateException("No sessions available"))
                }
                CompletableFuture.completedFuture(session)
            }

        tailClose = result

        return result
    }

    override fun dispose() {
        val toCancel: List<ThymianCLISession>
        synchronized(lock) {
            toCancel = pendingRequests.toList()
            pendingRequests.clear()
        }

        toCancel.forEach { it.completeExceptionally(CancellationException("Disposed")) }
    }

    private fun addSession(session: ThymianCLISession) {
        synchronized(lock) {
            pendingRequests.add(session)
        }
    }

    private fun removeSession(): ThymianCLISession? {
        synchronized(lock) {
            return pendingRequests.removeFirstOrNull()
        }
    }
}