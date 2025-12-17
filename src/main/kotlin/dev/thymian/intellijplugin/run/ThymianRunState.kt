package dev.thymian.intellijplugin.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable


class ThymianRunState(
    val environment: ExecutionEnvironment,
    val configuration: ThymianRunConfiguration
) : RunProfileState {
    private val project = environment.project

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        // If things like the Spring controller tooling are not initialized yet, we must read the endpoints
        // wrapped in synchronous processing, because we trigger the initialization.
        // If we don't wrap it, things will crash.
        val runProxies = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            ReadAction.compute<Sequence<ThymianRunProxy<*, *>>, Throwable> {
                if (configuration.runSettings.sortedEndpoints.isEmpty()) {
                    getRunProxiesFromProviders()
                } else {
                    getRunProxiesFromEndpoints(configuration.runSettings.sortedEndpoints)
                }
            }
        }, "", true, null)

        val properties = SMTRunnerConsoleProperties(
            configuration,
            "Thymian",
            environment.executor
        )
        val console = SMTestRunnerConnectionUtil.createConsole(properties)
        val processHandler = ThymianRunProcessHandler(console.resultsViewer.testsRootNode, runProxies)
        console.attachToProcess(processHandler)

        return DefaultExecutionResult(
            console,
            processHandler
        )
    }

    private fun getRunProxiesFromProviders(): Sequence<ThymianRunProxy<*, *>> {
        return EndpointsProvider.getAvailableProviders(project)
            .filter { it.isUsable() }
            .map { endpointProvider -> ThymianRunProxy(project, endpointProvider) }
    }

    private fun EndpointsProvider<*, *>.isUsable(): Boolean {
        return getStatus(project) != EndpointsProvider.Status.UNAVAILABLE
    }

    private fun getRunProxiesFromEndpoints(endpoints: List<ThymianRunSettings.SortedEndpoints<*, *>>): Sequence<ThymianRunProxy<*, *>> {
        return endpoints.map { ThymianRunProxy(project, it) }
            .asSequence()
    }
}

