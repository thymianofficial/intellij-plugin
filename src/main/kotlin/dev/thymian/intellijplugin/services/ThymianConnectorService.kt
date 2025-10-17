package dev.thymian.intellijplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.thymian.intellijplugin.models.Init
import dev.thymian.intellijplugin.models.InitPayload
import dev.thymian.intellijplugin.models.Message
import dev.thymian.intellijplugin.models.Payload
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

@Service(Service.Level.PROJECT)
class ThymianConnectorService(project: Project, private val cs: CoroutineScope) {
    private val pluginName = "intellij-plugin"
    private val port = 48294
    private var socket: Socket? = null
    private var `in`: BufferedReader? = null
    private var out: PrintWriter? = null
    private val cliProcess: Process?
    private var listenJob: Job? = null

    private val actionResponseListeners = mutableMapOf<String, (message: Payload.ResponsePayload) -> Unit>()

    init {
        thisLogger().info("Connecting ${project.name} to Thymian CLI")
        cliProcess = startCli()
        connect()
    }

    private fun startCli(): Process? {
        val builder = ProcessBuilder()
        builder.command("/home/andreas/Projects/thymian/thymian-docs/thymian/cli/bin/run.js", "run", "--tcp-client", pluginName, "-o", "@thymian/tcp-proxy.timeout=10000")
//    return builder.start()
        return null
    }

    private fun connect() {
        cs.launch {
            withContext(Dispatchers.IO) {
                while (socket?.isConnected != true) {
                    try {
                        socket = Socket("localhost", port)
                    } catch (_: IOException) {
                        delay(500)
                    }
                }
            }
            println("### CONNECTED ###")
            socket?.let {
                `in` = BufferedReader(InputStreamReader(it.getInputStream()))
                out = PrintWriter(it.getOutputStream(), true)
            }

            listenJob = listenForMessages()

            println("SENDING INIT")
            val initMessage = Json.encodeToString(
                Init(
                    InitPayload(
                        name = pluginName,
                        actions = InitPayload.Listeners(listOf("core.load-format")),
                        events = InitPayload.Listeners(listOf())
                    )
                )
            )
            println(initMessage)
            out?.println(initMessage)

            listenJob?.invokeOnCompletion {
                println("### DISCONNECT ###")
                disconnect()
            }
        }
    }

    private fun listenForMessages(): Job {
        return cs.launch {
            val `in` = this@ThymianConnectorService.`in` ?: return@launch
            withContext(Dispatchers.IO) {
                while (true) {
                    delay(50)
                    val messageStr = `in`.readLine() ?: continue
                    println("MESSAGE: $messageStr")
                    cs.launch(Dispatchers.Default) {
                        val message = Json.decodeFromString<Message>(messageStr)
                        handleEvent(message)
                    }
                }
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
        out?.println(Json.encodeToString(Message.Event(eventPayload)))
    }

    fun sendAction(
        actionPayload: Payload.EventPayload,
        listener: (Payload.ResponsePayload) -> Unit
    ) {
        actionResponseListeners[actionPayload.id] = { listener(it) }

        cs.launch {
            val message = Json.encodeToString(Message.Event(actionPayload))
            withContext(Dispatchers.IO) {
                out?.println(message)
            }
        }
    }

    private fun disconnect() {
        socket?.close()
        socket = null
        cliProcess?.destroy()
    }
}