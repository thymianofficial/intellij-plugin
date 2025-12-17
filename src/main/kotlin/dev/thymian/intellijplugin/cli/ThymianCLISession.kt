package dev.thymian.intellijplugin.cli

import java.util.concurrent.CompletableFuture

internal class ThymianCLISession(private val cli: ThymianCLI) : ThymianCLI by cli, CompletableFuture<Unit>() {
    override fun initialize(): CompletableFuture<Unit> {
        if (isCancelled || isCompletedExceptionally) {
            return this
        }
        return cli.initialize()
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
