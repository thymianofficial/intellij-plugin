package dev.thymian.intellijplugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.util.application
import java.util.concurrent.CompletableFuture

internal class ThymianProcessHandler(
    private val rootNode: SMTestProxy.SMRootTestProxy,
    private val runProxies: Sequence<ThymianRunProxy<*, *>>
) : ProcessHandler() {
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

            validRunProxies
                .fold(CompletableFuture<Unit>().completeAsync {}) { prev, runProxy ->
                    prev.thenCompose { runProxy.runTest() }
                }.thenRun {
                    application.invokeLater {
                        rootNode.setFinished()
                        notifyProcessTerminated(0)
                    }
                }
        }
    }

    override fun destroyProcessImpl() {
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        destroyProcessImpl()
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput() = null
}