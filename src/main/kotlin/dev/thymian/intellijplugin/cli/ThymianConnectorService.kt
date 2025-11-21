package dev.thymian.intellijplugin.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.thymian.intellijplugin.models.*
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
class ThymianConnectorService(project: Project, private val cs: CoroutineScope) {
    private val pluginName = "intellij-plugin"
    private val port = 51234
    private val cliProcess: Process?
    private var client: HttpClient? = null
    private var websocketSession: WebSocketSession? = null

    private val actionResponseListeners = mutableMapOf<String, ActionListener<*, *>>()

    init {
        thisLogger().info("Connecting ${project.name} to Thymian CLI")
        cliProcess = startCli()
        connect()
    }

    private fun startCli(): Process? {
        val builder = ProcessBuilder()
        builder.command(
            "/home/andreas/Projects/thymian/thymian-docs/thymian/cli/bin/run.js",
            "run",
            "--tcp-client",
            pluginName,
            "-o",
            "@thymian/tcp-proxy.timeout=10000"
        )
//    return builder.start()
        return null
    }

    private fun connect() {
        cs.launch {
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

            val listenJob = listenForMessages()

            val readyMessage = Json.encodeToString(Ready())
            websocketSession?.send(readyMessage)
            thisLogger().info("Connected to Thymian CLI")

            listenJob.invokeOnCompletion {
                runBlocking {
                    println("### DISCONNECT ###")
                    disconnect()
                }
            }
        }
    }

    private fun buildRequest(builder: HttpRequestBuilder) = builder.apply {
        method = HttpMethod.Get
        url(host = "localhost", port = port, path = "/")
    }

    private fun listenForMessages(): Job = cs.launch {
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
        }.onFailure {
            thisLogger().error("Error while listening for messages", it)
        }
    }

    private fun handleEvent(message: Receiving) {
        println("##### EVENT ##### $message")
        when (message) {
            is Receiving.EventMessage -> {
                // TODO handle events
            }

            is Receiving.ActionMessage -> {
                // TODO handle actions
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

    fun <T : Any> sendEvent(event: EmitEventMessage<T>) {
        cs.launch {
            val messageString = Json.encodeToString(event)
            websocketSession?.send(messageString)
        }
    }

    fun <S : ActionResultMessage<T>, T : Any> sendAction(
        action: EmitActionMessage,
        listener: ActionListener<S, T>
    ) {
        actionResponseListeners[action.id] = listener

        cs.launch {
            val messageString = Json.encodeToString(action)
            websocketSession?.send(messageString)
        }
    }

    private suspend fun disconnect() {
        websocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Normal close"))
        websocketSession = null
        client?.close()
        client = null
        cliProcess?.destroy()
    }
}
