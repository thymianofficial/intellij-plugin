package dev.thymian.client.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.application
import dev.thymian.client.cli.ThymianCLI
import dev.thymian.client.cli.ThymianCLISessionManager
import java.util.concurrent.CompletableFuture

internal class ThymianRunProcessHandler(
    private val project: Project,
    private val rootNode: SMTestProxy.SMRootTestProxy,
    private val runProxies: Sequence<ThymianRunProxy<*, *>>
) : ProcessHandler() {
    private var thymianCLI: ThymianCLI? = null

    private fun prepare() = ReadAction.computeBlocking<List<ThymianRunProxy<*, *>>, Throwable> {
        runProxies
            .onEach { it.initialize() }
            .toList()
            .also { validProxies ->
                validProxies.map { it.smTestProxy }
                    .forEach { rootNode.addChild(it) }
            }
    }

    override fun startNotify() {
        super.startNotify()

        application.executeOnPooledThread<Unit> {
            rootNode.setSuiteStarted()

            val validRunProxies = prepare()

            ThymianCLISessionManager.getInstance().getThymianCLI(project)
                .thenCompose { thymianCLI ->
                    this.thymianCLI = thymianCLI
                    thymianCLI.initialize { rootNode.addStdOutput("$it\n") }
                        .thenApply { thymianCLI }
                }
                .thenCompose { thymianCLI ->
                    validRunProxies.fold(CompletableFuture<Unit>().completeAsync {}) { prev, runProxy ->
                        prev.thenCompose { runProxy.runTest(thymianCLI) }
                    }.thenCompose { thymianCLI.close() }
                }.thenRun {
                    application.invokeLater {
                        rootNode.setFinished()
                        notifyProcessTerminated(0)
                    }
                }
                .exceptionally { ex ->
                    rootNode.setFinished()
                    rootNode.setTestFailed(ex.message, ex.stackTrace.contentToString(), true)
                    destroyProcessImpl()
                    null
                }
        }
    }

    override fun destroyProcessImpl() {
        thymianCLI?.close()
        thymianCLI = null
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        destroyProcessImpl()
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput() = null
}