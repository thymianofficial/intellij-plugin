package dev.thymian.client.cli

import java.util.concurrent.CompletableFuture

internal class ThymianCLISession(private val cli: ThymianCLI) : ThymianCLI by cli, CompletableFuture<Unit>() {
    override fun initialize(messageListener: (String) -> Unit): CompletableFuture<Unit> {
        if (isCancelled || isCompletedExceptionally) {
            return this
        }
        return cli.initialize(messageListener)
    }

    override fun close(): CompletableFuture<Unit> {
        return cli.close().whenComplete { _, ex ->
            if (ex == null) {
                complete(Unit)
            } else {
                completeExceptionally(ex)
            }
        }
    }
}
