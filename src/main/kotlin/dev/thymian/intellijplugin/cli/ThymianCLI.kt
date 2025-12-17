package dev.thymian.intellijplugin.cli

import java.util.concurrent.CompletableFuture

internal interface ThymianCLI {
    fun initialize(): CompletableFuture<Unit>

    fun <T : Any> sendEvent(event: EmitEventMessage<T>)

    fun <S : ActionResultMessage<T>, T : Any> sendAction(action: EmitActionMessage, listener: ActionListener<S, T>)

    fun close(): CompletableFuture<Unit>
}