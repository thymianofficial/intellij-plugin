package dev.thymian.intellijplugin.cli

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.awaitExit
import dev.thymian.intellijplugin.settings.ThymianSettingsState
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ThymianCLILocalRunner(
    private val cli: ThymianCLI,
    private val settings: ThymianSettingsState.State,
    private val cs: CoroutineScope
) : ThymianCLI by cli {
    private lateinit var cliProcess: Process

    override fun initialize(): CompletableFuture<Unit> = cs.launch {
        val (directory, command) = getProcessAndDirectory()
        buildCliProcess(directory, command)
        waitForServeMode()
    }.asCompletableFuture()
        .thenCompose { cli.initialize() }

    private fun buildCliProcess(directory: String, command: String) {
        val builder = ProcessBuilder()
        builder.directory(File(directory))
            .command(
                command,
                "serve",
                "-o",
                "@thymian/websocket-proxy.port=${settings.websocketPort}"
            )
        cliProcess = builder.start()
    }

    private fun getProcessAndDirectory(): Pair<String, String> {
        val cliPath = settings.thymianCliPath
        val binIndex = ThymianSettingsState.RUN_FILE_OPTIONS.map { cliPath.indexOf(it) }.find { it != -1 }
        val directory = if (binIndex != null) cliPath.substring(0, binIndex) else ""
        val command = if (binIndex != null) cliPath.substring(binIndex + 1) else cliPath
        return Pair(directory, command)
    }

    private suspend fun waitForServeMode() {
        val reader = cliProcess.inputReader()
        val errorReader = cliProcess.errorReader()

        val timeoutMillis = 30000L // 30 seconds timeout
        val startTime = System.currentTimeMillis()

        var startecCli = false
        withContext(Dispatchers.IO) {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (reader.ready()) {
                    val line = reader.readLine()
                    thisLogger().info("CLI output: $line")
                    if (line?.contains("Thymian is now in \"serve\" mode") == true) {
                        thisLogger().info("Thymian CLI is ready")
                        startecCli = true
                        break
                    }
                }

                if (errorReader.ready()) {
                    val errorLine = errorReader.readLine()
                    thisLogger().warn("CLI error output: $errorLine")
                }

                if (!cliProcess.isAlive) {
                    throw IllegalStateException("Thymian CLI process terminated unexpectedly")
                }

                delay(100)
            }
        }

        if (!startecCli) {
            throw IllegalStateException("Thymian CLI did not enter serve mode within timeout period")
        }
    }

    override fun close(): CompletableFuture<Unit> = cli.close()
        .thenCompose { stopCliProcess() }
        .exceptionallyCompose { stopCliProcess() }

    private fun stopCliProcess() = cs.launch {
        withContext(Dispatchers.IO) {
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