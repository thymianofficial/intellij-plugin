package dev.thymian.client.cli

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.awaitExit
import dev.thymian.client.settings.ThymianSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class ThymianCLILocalRunner(
    private val cli: ThymianCLI,
    private val project: Project,
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

    private fun buildCliProcess(directory: String, command: List<String>) {
        val builder = ProcessBuilder()
        builder.directory(File(directory))
            .command(
                command + listOf(
                    "serve",
                    "--rule-severity=hint",
                    "-o",
                    "@thymian/plugin-websocket-proxy.port=${settings.websocketPort}"
                )
            )
        messageListener("Starting Thymian CLI process: ${builder.command()}")
        cliProcess = builder.start()
    }

    private fun getProcessAndDirectory(): Pair<String, List<String>> {
        val cliPath = settings.thymianCliPath
        if (cliPath.isBlank()) {
            // No CLI path configured: run the latest Thymian via npx, downloading it if
            // necessary. "--yes" skips npx's install confirmation prompt. On Windows the
            // executable on PATH is "npx.cmd", which ProcessBuilder won't resolve from a
            // bare "npx".
            val npx = if (SystemInfo.isWindows) "npx.cmd" else "npx"
            return Pair(project.basePath!!, listOf(npx, "--yes", "thymian@latest"))
        }
        val binIndex = ThymianSettings.RUN_FILE_OPTIONS.map { cliPath.indexOf(it) }.find { it != -1 }
        val directory = if (binIndex != null) cliPath.substring(0, binIndex) else ""
        val command = if (binIndex != null) cliPath.substring(binIndex + 1) else cliPath
        return Pair(directory, listOf(command))
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

                delay(100.milliseconds)
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