package dev.thymian.intellijplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.thymian.intellijplugin.models.Init
import dev.thymian.intellijplugin.models.InitPayload
import dev.thymian.intellijplugin.models.Message
import dev.thymian.intellijplugin.models.Payload
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@Service(Service.Level.PROJECT)
class ThymianConnectorService(project: Project, private val cs: CoroutineScope) {
    private val pluginName = "intellij-plugin"
    private val port = 48294
    private val cliProcess: Process?
    private var client: HttpClient? = null

    private val actionResponseListeners = mutableMapOf<String, (message: Payload.ResponsePayload) -> Unit>()

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
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(Json)
                }
            }

            val listenJob = listenForMessages()

            println("SENDING INIT")
            val initMessage = Init(
                InitPayload(
                    name = pluginName,
                    actions = InitPayload.Listeners(listOf("core.load-format")),
                    events = InitPayload.Listeners(listOf())
                )
            )

            client?.webSocket({ buildRequest(this) }) {
                sendSerialized(initMessage)
            }

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
        client?.webSocket({ buildRequest(this) }) {
            while (true) {
                val message = receiveDeserialized<Message>()
                cs.launch(Dispatchers.Default) { handleEvent(message) }
            }
        }
    }

    private fun handleEvent(message: Message) {
        println("##### EVENT ##### ${message.payload}")
        when (message) {
            is Message.Event -> {
                // TODO handle events and actions
            }

            is Message.Response -> {
                actionResponseListeners.remove(message.payload.correlationId)?.invoke(message.payload)
            }

            is Message.Error -> {
                // TODO handle errors
            }
        }
    }

    fun sendEvent(eventPayload: Payload.EventPayload) {
        cs.launch {
            client?.webSocket({ buildRequest(this) }) {
                sendSerialized(Message.Event(eventPayload))
            }
        }
    }

    fun sendAction(
        actionPayload: Payload.EventPayload,
        listener: (Payload.ResponsePayload) -> Unit
    ) {
        actionResponseListeners[actionPayload.id] = { listener(it) }

        cs.launch {
            client?.webSocket({ buildRequest(this) }) {
                sendSerialized(Message.Event(actionPayload))
            }
        }
    }

    private fun disconnect() {
        client?.close()
        client = null
        cliProcess?.destroy()
    }
}