package dev.thymian.client.endpoints

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.microservices.endpoints.API_DEFINITION_TYPE
import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsFilter
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.FrameworkPresentation
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.thymian.client.cli.ThymianCLISessionManager
import dev.thymian.client.run.ThymianRunProxy
import dev.thymian.client.run.ThymianRunSettings
import dev.thymian.client.settings.ThymianSettings
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Real-CLI end-to-end test: drives the plugin's production CLI stack
 * ([dev.thymian.client.cli.SequentialThymianCLISessionManager] →
 * [dev.thymian.client.cli.ThymianCLILocalRunner] → [dev.thymian.client.cli.ThymianCLIAdapter])
 * against a freshly built thymian CLI over a real websocket — one `core.workflow.lint`
 * round-trip. The plugin's own protocol client is the contract under test; drift against
 * thymian@main surfaces here first.
 *
 * Needs the CLI entry point of a built thymian checkout:
 * `./gradlew e2eTest -PthymianCliPath=<thymian checkout>/packages/thymian/bin/dev.js`
 * (or the `THYMIAN_CLI_PATH` environment variable).
 *
 * Assertions target the mechanism, not rule outcomes — rule results may legitimately drift
 * with thymian@main; a transport or action error must not.
 */
class ThymianRealCliE2eTest : BasePlatformTestCase() {
    private lateinit var testEndpoints: FakeApiDefinitionEndpoints
    private var settings: ThymianSettings? = null
    private var originalCliPath: String = ""
    private var originalPort: Int = ThymianSettings.DEFAULT_WEBSOCKET_PORT
    private var allocatedPort: Int = 0

    override fun setUp() {
        super.setUp()

        val cliPath = System.getProperty("thymianCliPath")
        if (cliPath.isNullOrBlank()) {
            fail(
                "Missing system property 'thymianCliPath'. Run via " +
                    "./gradlew e2eTest -PthymianCliPath=<thymian checkout>/packages/thymian/bin/dev.js " +
                    "(or set THYMIAN_CLI_PATH); the Gradle task forwards it."
            )
        }
        // The checks mirror what the production runner needs: it splits the path at the first
        // RUN_FILE_OPTIONS match into working directory + relative command and spawns the
        // entry directly via its shebang — so the path must be absolute (setUp's cwd and the
        // spawn directory differ), executable, and carry a known entry suffix, or the spawn
        // fails later with an opaque IOException instead of this message.
        val cliFile = File(cliPath!!)
        if (!cliFile.isAbsolute) {
            fail("'thymianCliPath' must be an absolute path, got: $cliPath")
        }
        if (!cliFile.isFile || !cliFile.canRead()) {
            fail(
                "'thymianCliPath' does not point to a readable file: $cliPath. " +
                    "Pass the CLI entry point of a built thymian checkout, e.g. " +
                    "-PthymianCliPath=<thymian checkout>/packages/thymian/bin/dev.js"
            )
        }
        if (!cliFile.canExecute()) {
            fail(
                "'thymianCliPath' is not executable: $cliPath. The production runner spawns " +
                    "it directly (shebang) — restore the exec bit (chmod +x)."
            )
        }
        if (ThymianSettings.RUN_FILE_OPTIONS.none { cliPath.contains(it) }) {
            fail(
                "'thymianCliPath' contains none of ${ThymianSettings.RUN_FILE_OPTIONS} — the " +
                    "production runner splits the path at that entry; point it at " +
                    "<thymian checkout>/packages/thymian/bin/dev.js"
            )
        }

        testEndpoints = FakeApiDefinitionEndpoints(project)
        ExtensionTestUtil.maskExtensions(
            EndpointsProvider.EP_NAME,
            listOf(testEndpoints.endpointsProvider),
            testRootDisposable
        )

        // Settings are mutated LAST — nothing below can throw, so a failed setUp never leaks
        // a mutated application-level service (JUnit 3 skips tearDown when setUp throws).
        // The service is the production control surface: runner + adapter read its State by
        // reference, and it outlives the test, so tearDown restores the captured originals.
        val settings = ThymianSettings.getInstance()
        this.settings = settings
        originalCliPath = settings.thymianCliPath
        originalPort = settings.websocketPort
        settings.thymianCliPath = cliPath
        // The 51234 default would collide with any thymian instance already running on this
        // machine; a freshly bound-and-closed port keeps the e2e isolated.
        allocatedPort = ServerSocket(0).use { it.localPort }
        settings.websocketPort = allocatedPort
    }

    override fun tearDown() {
        try {
            settings?.let {
                it.thymianCliPath = originalCliPath
                it.websocketPort = originalPort
            }
            killLeftoverCliProcess()
            val editorFactory = EditorFactory.getInstance()
            editorFactory.allEditors.forEach { editor ->
                if (!editor.isDisposed) {
                    editorFactory.releaseEditor(editor)
                }
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test one real lint round-trip through the production CLI stack`() {
        val resultsViewer = createTestResultsViewer(project, testRootDisposable)
        val sortedEndpoints = ThymianRunSettings.SortedEndpoints(testEndpoints.endpointsProvider, testEndpoints.items)
        val proxy = ThymianRunProxy(project, sortedEndpoints, resultsViewer)

        val cliFuture = ThymianCLISessionManager.getInstance().getThymianCLI(project)
        val roundTrip = cliFuture
            .thenCompose { cli -> cli.initialize { line -> println("CLI: $line") }.thenApply { cli } }
            .thenCompose { cli -> proxy.runTest(cli) }

        // Close best-effort once the round-trip settles, whatever its outcome — the
        // production quit path has no kill escalation, so skipping close on failure would
        // orphan a real `serve` process.
        val closeFuture = roundTrip.handle { _, _ -> }.thenCompose {
            val cli = if (cliFuture.isDone && !cliFuture.isCompletedExceptionally) cliFuture.getNow(null) else null
            cli?.close() ?: CompletableFuture.completedFuture(Unit)
        }

        // ThymianRunProxy posts results via invokeLater — without pumping the EDT the
        // futures never complete. Known bounds: 30s spawn banner (client-enforced), 4s
        // handshake window (server-enforced), 60s action timeout (server-honored). The
        // adapter itself waits unbounded, so this deadline is the only client-side backstop.
        val settled = pumpUntil(DEADLINE_MILLIS) { closeFuture.isDone }
        if (!settled) {
            // A hung chain is the likeliest failure shape (see above) — close best-effort
            // and hard-kill any surviving CLI child before failing, or the real `serve`
            // process outlives the test.
            val cli = if (cliFuture.isDone && !cliFuture.isCompletedExceptionally) cliFuture.getNow(null) else null
            val lateClose = cli?.let { runCatching(it::close).getOrNull() }
            if (lateClose != null) {
                pumpUntil(LATE_CLOSE_GRACE_MILLIS) { lateClose.isDone }
            }
            killLeftoverCliProcess()
            fail("CLI session (spawn → register → lint → close) did not settle within ${DEADLINE_MILLIS / 1000}s")
        }

        if (roundTrip.isCompletedExceptionally) {
            val error = runCatching { roundTrip.join() }.exceptionOrNull()
            // The adapter stack IS the drift diagnosis — keep it attached, not toString()ed.
            throw AssertionError("lint round-trip completed exceptionally: $error", error)
        }
        assertFalse("cli.close() completed exceptionally", closeFuture.isCompletedExceptionally)

        // A real Report arrived and was grouped: at least one location child under the
        // test-set (file) proxy. Rule failures (isDefect) are acceptable — CI runs against
        // thymian@main; an adapter/action error message is not.
        assertEquals("expected exactly one file suite under the provider proxy", 1, proxy.smTestProxy.children.size)
        val fileProxy = proxy.smTestProxy.children.single()
        assertNull("adapter/action error surfaced: ${fileProxy.errorMessage}", fileProxy.errorMessage)
        assertTrue(
            "expected at least one grouped location under '${fileProxy.name}' — no Report content arrived",
            fileProxy.children.isNotEmpty()
        )
        fileProxy.children.forEach { locationProxy ->
            assertNull(
                "adapter/action error on '${locationProxy.name}': ${locationProxy.errorMessage}",
                locationProxy.errorMessage
            )
        }
    }

    private fun pumpUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                return false
            }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(50)
        }
        return true
    }

    /**
     * Kills any CLI process this test spawned that is still alive — identified among the test
     * JVM's descendants by the unique per-run port in its command line. Belt-and-braces for the
     * hung-chain path; a no-op when the session closed normally (or nothing was spawned).
     */
    private fun killLeftoverCliProcess() {
        if (allocatedPort == 0) {
            return
        }
        val marker = "@thymian/plugin-websocket-proxy.port=$allocatedPort"
        ProcessHandle.current().descendants()
            .filter { handle -> handle.info().commandLine().map { it.contains(marker) }.orElse(false) }
            .forEach { handle ->
                handle.destroy()
                runCatching { handle.onExit().get(5, TimeUnit.SECONDS) }.onFailure { handle.destroyForcibly() }
            }
    }

    companion object {
        private const val DEADLINE_MILLIS = 180_000L
        private const val LATE_CLOSE_GRACE_MILLIS = 30_000L
    }
}

/**
 * Builds a real [SMTestRunnerResultsForm] the way production code does (`ThymianRunState.execute`),
 * so the proxy can post the results-viewer notifications it requires. Copied from
 * `ThymianEndpointsRunCheckIntegrationTest` — file-private helpers can't cross source sets.
 */
private fun createTestResultsViewer(project: Project, disposable: Disposable): SMTestRunnerResultsForm {
    val runProfile = object : RunProfile {
        override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
        override fun getName(): String = "ThymianE2eTest"
        override fun getIcon() = null
    }
    val properties = SMTRunnerConsoleProperties(project, runProfile, "ThymianE2eTest", DefaultRunExecutor.getRunExecutorInstance())
    val console = SMTestRunnerConnectionUtil.createConsole(properties)
    Disposer.register(disposable, console)
    return console.resultsViewer
}

private typealias Group = String
private typealias Endpoint = String

/**
 * IDE-side endpoints fixture (not a CLI stub): supplies a real OpenAPI document through the
 * endpoints framework so the production stack has something to lint. Copied from
 * `ThymianEndpointsRunCheckIntegrationTest` — the original is file-private.
 */
private class FakeApiDefinitionEndpoints(project: Project) {
    val fileContent = """
openapi: 3.0.0
info:
  title: TestAPI
  version: "1.0"
paths:
  /:
    get:
      parameters: [ ]
      responses:
        "200":
          description: ""
""".trimIndent()
    private val groups = listOf("test-group")
    private val endpoints = listOf("endpoint")
    private val file by lazy {
        PsiFileFactory.getInstance(project).createFileFromText(
            "openapi.yaml",
            com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE,
            fileContent
        )
    }

    val endpointsProvider = object : EndpointsProvider<Group, Endpoint> {
        override val presentation = FrameworkPresentation("ThymianE2eTest", "E2e Endpoints Provider", null)
        override val endpointType = API_DEFINITION_TYPE

        override fun getEndpointGroups(project: Project, filter: EndpointsFilter) =
            if (filter is ModuleEndpointsFilter) {
                emptyList()
            } else {
                groups
            }

        override fun getEndpoints(group: Group) = if (groups.contains(group)) {
            endpoints
        } else {
            emptyList()
        }

        override fun getNavigationElement(group: Group, endpoint: Endpoint): PsiFile? {
            return if (groups.contains(group) && endpoints.contains(endpoint)) {
                file
            } else {
                null
            }
        }

        override fun getStatus(project: Project) = EndpointsProvider.Status.AVAILABLE

        override fun isValidEndpoint(group: Group, endpoint: Endpoint) = true

        override fun getModificationTracker(project: Project) = ModificationTracker.NEVER_CHANGED

        override fun getEndpointPresentation(group: Group, endpoint: Endpoint): ItemPresentation {
            return object : ItemPresentation {
                override fun getPresentableText(): String {
                    return "$group:$endpoint"
                }

                override fun getIcon(p0: Boolean) = null
            }
        }
    }

    val items = groups.flatMap { g ->
        endpoints.map { e ->
            object : EndpointsElementItem<Group, Endpoint> {
                override val module = null
                override val provider = endpointsProvider
                override val group = g
                override val endpoint = e
                override val isValid = true
            }
        }
    }
}
