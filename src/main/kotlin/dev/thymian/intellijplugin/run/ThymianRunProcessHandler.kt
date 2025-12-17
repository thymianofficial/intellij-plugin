package dev.thymian.intellijplugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.util.application
import dev.thymian.intellijplugin.cli.ThymianCLI
import dev.thymian.intellijplugin.cli.ThymianCLISessionManager
import java.util.concurrent.CompletableFuture

internal class ThymianRunProcessHandler(
    private val rootNode: SMTestProxy.SMRootTestProxy,
    private val runProxies: Sequence<ThymianRunProxy<*, *>>
) : ProcessHandler() {
    private var thymianCLI: ThymianCLI? = null

    private fun prepare() = ReadAction.compute<List<ThymianRunProxy<*, *>>, Throwable> {
        runProxies
            .onEach { it.initialize() }
            .filter { it.hasTestData }
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

            application.getService(ThymianCLISessionManager::class.java).getThymianCLI()
                .thenCompose { thymianCLI ->
                    this.thymianCLI = thymianCLI
                    thymianCLI.initialize().thenApply { thymianCLI }
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