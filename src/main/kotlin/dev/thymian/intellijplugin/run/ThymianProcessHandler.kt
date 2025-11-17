package dev.thymian.intellijplugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.util.application

internal class ThymianProcessHandler(
    private val rootNode: SMTestProxy.SMRootTestProxy,
    private val runProxies: Sequence<ThymianRunProxy<*, *>>
) : ProcessHandler() {
    override fun startNotify() {
        super.startNotify()

        val testNodes = application.executeOnPooledThread<List<SMTestProxy>> {
            rootNode.setSuiteStarted()

            ReadAction.compute<List<SMTestProxy>, Throwable> {
                runProxies
                    .onEach { it.initialize() }
                    .filter { it.hasTestData }
                    .map { it.smTestProxy }
                    .onEach { rootNode.addChild(it) }
                    .toList()
            }
        }.get()

        application.executeOnPooledThread {
            ReadAction.run<Throwable> {
                testNodes.forEach { testNode ->
                    Thread.sleep(200)
                    application.invokeLater {
                        testNode.addStdOutput("processing\n")
                    }
                    Thread.sleep(200)

                    // Run the actual test off the EDT
                    val success = true

                    application.invokeLater {
                        testNode.addStdOutput("done\n")
                        if (!success) {
                            testNode.setTestFailed(
                                "Validation failed",
                                /*stacktrace*/null,
                                /*Failed or Errored*/false
                            )
                        }
                        testNode.setFinished()
                    }
                }

                // Mark the root/suite as finished and terminate the process
                application.invokeLater {
                    rootNode.setFinished()
                }

                application.invokeLater {
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