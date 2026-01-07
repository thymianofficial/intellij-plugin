package dev.thymian.intellijplugin.cli

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.awaitExit
import dev.thymian.intellijplugin.settings.ThymianSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ThymianCLILocalRunner(
    private val cli: ThymianCLI,
    private val settings: ThymianSettings.State,
    private val cs: CoroutineScope
) : ThymianCLI by cli {
    private lateinit var cliProcess: Process
    private lateinit var messageListener: (String) -> Unit
    private var listenJob: Job? = null

    override fun initialize(messageListener: (String) -> Unit): CompletableFuture<Unit> = cs.launch {
        this@ThymianCLILocalRunner.messageListener = messageListener
        val (directory, command) = getProcessAndDirectory()
        buildCliProcess(directory, command)
        waitForServeMode()
        listenJob = forwardMessages()
    }.asCompletableFuture()
        .thenCompose { cli.initialize(this@ThymianCLILocalRunner.messageListener) }

    private fun buildCliProcess(directory: String, command: String) {
        val builder = ProcessBuilder()
        builder.directory(File(directory))
            .command(
                command,
                "serve",
                "-o",
                "@thymian/websocket-proxy.port=${settings.websocketPort}"
            )
        messageListener("Starting Thymian CLI process: ${builder.command()}")
        cliProcess = builder.start()
    }

    private fun getProcessAndDirectory(): Pair<String, String> {
        val cliPath = settings.thymianCliPath
        val binIndex = ThymianSettings.RUN_FILE_OPTIONS.map { cliPath.indexOf(it) }.find { it != -1 }
        val directory = if (binIndex != null) cliPath.substring(0, binIndex) else ""
        val command = if (binIndex != null) cliPath.substring(binIndex + 1) else cliPath
        return Pair(directory, command)
    }

    private suspend fun waitForServeMode() {
        val reader = cliProcess.inputReader()
        val errorReader = cliProcess.errorReader()

        val timeoutMillis = 30000L // 30 seconds timeout
        val startTime = System.currentTimeMillis()

        var startedCli = false
        withContext(Dispatchers.IO) {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (reader.ready()) {
                    val line = reader.readLine()
                    line?.trim()?.let { messageListener(it) }
                    thisLogger().info("CLI output: $line")
                    if (line?.contains("Thymian is now in \"serve\" mode") == true) {
                        thisLogger().info("Thymian CLI is ready")
                        startedCli = true
                        break
                    }
                }

                if (errorReader.ready()) {
                    val errorLine = errorReader.readLine()
                    errorLine?.trim()?.let { messageListener(it) }
                    thisLogger().warn("CLI error output: $errorLine")
                }

                if (!cliProcess.isAlive) {
                    throw IllegalStateException("Thymian CLI process terminated unexpectedly")
                }

                delay(100)
            }
        }

        if (!startedCli) {
            throw IllegalStateException("Thymian CLI did not enter serve mode within timeout period")
        }
    }

    private fun forwardMessages(): Job {
        return cs.launch(Dispatchers.IO) {
            val reader = cliProcess.inputReader()
            val errorReader = cliProcess.errorReader()
            while (cliProcess.isAlive && !currentCoroutineContext().job.isCancelled) {
                if (reader.ready()) {
                    reader.readLine()?.trim()?.let { messageListener(it) }
                }
                if (errorReader.ready()) {
                    errorReader.readLine()?.trim()?.let { messageListener(it) }
                }
            }
        }
    }

    override fun close(): CompletableFuture<Unit> = cli.close()
        .thenCompose { stopCliProcess() }
        .exceptionallyCompose { stopCliProcess() }

    private fun stopCliProcess() = cs.launch {
        withContext(Dispatchers.IO) {
            listenJob?.cancelAndJoinSilently()
            if (!cliProcess.isAlive) {
                return@withContext
            }
            cliProcess.outputWriter().apply {
                write("q")
                flush()
            }
            cliProcess.awaitExit()
            thisLogger().info("Thymian CLI process terminated")
        }
    }.asCompletableFuture()
        .orTimeout(10, TimeUnit.SECONDS)
}