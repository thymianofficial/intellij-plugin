package dev.thymian.plugin.cli

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.openapi.diagnostic.thisLogger
import dev.thymian.plugin.settings.ThymianSettings
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture

internal class ThymianCLIAdapter(private val settings: ThymianSettings.State, private val cs: CoroutineScope) :
    ThymianCLI {
    private val pluginName = "intellij-plugin"
    private var client: HttpClient? = null
    private var websocketSession: WebSocketSession? = null
    private lateinit var initFuture: CompletableFuture<Unit>
    private lateinit var listenJob: Job

    private val actionResponseListeners = mutableMapOf<String, ActionListener<*, *>>()

    override fun initialize(messageListener: (String) -> Unit): CompletableFuture<Unit> {
        initFuture = cs.launch {
            client = HttpClient(Java) {
                install(WebSockets.Plugin)
            }

            websocketSession = client?.webSocketSession { buildRequest(this) }

            val registerMessage = Register(
                name = pluginName,
                onActions = listOf(),
                onEvents = listOf()
            )


            val messageString = Json.encodeToString(registerMessage)
            websocketSession?.send(messageString)

            val frame = websocketSession?.incoming?.receive()
            if (frame !is Frame.Text) {
                return@launch
            }
            val receivedText = frame.readText()
            val registerAck = Json.decodeFromString<RegisterResponse>(receivedText)
            if (!registerAck.ok) {
                throw IllegalStateException("Register not acknowledged")
            }

            listenJob = listenForMessages()

            val readyMessage = Json.encodeToString(Ready())
            websocketSession?.send(readyMessage)
            thisLogger().info("Connected to Thymian CLI")
        }.asCompletableFuture()

        return initFuture
    }

    private fun buildRequest(builder: HttpRequestBuilder) = builder.apply {
        method = HttpMethod.Get
        url(host = "localhost", port = settings.websocketPort, path = "/")
    }

    private fun listenForMessages() = cs.launch {
        runCatching {
            websocketSession?.incoming?.consumeEach { frame ->
                if (frame !is Frame.Text) {
                    return@consumeEach
                }
                val receivedText = frame.readText()
                cs.launch(Dispatchers.Default) {
                    val message = Json.decodeFromString<Receiving>(receivedText)
                    handleEvent(message)
                }
            }
        }
    }

    private fun handleEvent(message: Receiving) {
        when (message) {
            is Receiving.EventMessage -> {
                // nothing to do yet
            }

            is Receiving.ActionMessage -> {
                // nothing to do yet
            }

            is Receiving.ActionResultMessageWrapper -> {
                actionResponseListeners.remove(message.correlationId)
                    ?.handleResult(message.toTypedMessage())
            }

            is Receiving.ActionErrorMessage -> {
                actionResponseListeners.remove(message.correlationId)
                    ?.onError?.invoke(message)
            }
        }
    }

    override fun <T : Any> sendEvent(event: EmitEventMessage<T>) {
        cs.launch {
            initFuture.join()

            val messageString = Json.encodeToString(event)
            websocketSession?.send(messageString)
        }
    }

    override fun <S : ActionResultMessage<T>, T : Any> sendAction(
        action: EmitActionMessage,
        listener: ActionListener<S, T>
    ) {
        actionResponseListeners[action.id] = listener

        cs.launch {
            initFuture.join()

            val messageString = Json.encodeToString(action)
            websocketSession?.send(messageString)
        }
    }

    override fun close() = cs.launch {
        listenJob.cancelAndJoinSilently()
        websocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Normal close"))
        websocketSession = null
        client?.close()
        client = null
        actionResponseListeners.clear()
    }.asCompletableFuture()
}