package dev.thymian.client.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import dev.thymian.client.ThymianBundle


class ThymianRunState(
    val environment: ExecutionEnvironment,
    val configuration: ThymianRunConfiguration
) : RunProfileState {
    private val project = environment.project

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        // The console/results form must exist before we build run proxies, since each proxy
        // needs a reference to it to notify the SM tree of nodes it adds later (see
        // ThymianRunProxy — merely mutating SMTestProxy doesn't refresh the visible tree).
        val properties = SMTRunnerConsoleProperties(
            configuration,
            ThymianBundle.message("runConfiguration.name"),
            environment.executor
        )
        val console = SMTestRunnerConnectionUtil.createConsole(properties)
        val resultsViewer = console.resultsViewer

        // If things like the Spring controller tooling are not initialized yet, we must read the endpoints
        // wrapped in synchronous processing, because we trigger the initialization.
        // If we don't wrap it, things will crash.
        val runProxies = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            ReadAction.computeBlocking<Sequence<ThymianRunProxy<*, *>>, Throwable> {
                if (configuration.runSettings.sortedEndpoints.isEmpty()) {
                    getRunProxiesFromProviders(resultsViewer)
                } else {
                    getRunProxiesFromEndpoints(configuration.runSettings.sortedEndpoints, resultsViewer)
                }
            }
        }, "", true, null)

        val processHandler =
            ThymianRunProcessHandler(environment.project, resultsViewer.testsRootNode, runProxies)
        console.attachToProcess(processHandler)

        return DefaultExecutionResult(
            console,
            processHandler
        )
    }

    private fun getRunProxiesFromProviders(resultsViewer: SMTestRunnerResultsForm): Sequence<ThymianRunProxy<*, *>> {
        return EndpointsProvider.getAvailableProviders(project)
            .filter { it.isUsable() }
            .map { endpointProvider -> ThymianRunProxy(project, endpointProvider, resultsViewer) }
    }

    private fun EndpointsProvider<*, *>.isUsable(): Boolean {
        return getStatus(project) != EndpointsProvider.Status.UNAVAILABLE
    }

    private fun getRunProxiesFromEndpoints(
        endpoints: List<ThymianRunSettings.SortedEndpoints<*, *>>,
        resultsViewer: SMTestRunnerResultsForm
    ): Sequence<ThymianRunProxy<*, *>> {
        return endpoints.map { ThymianRunProxy(project, it, resultsViewer) }
            .asSequence()
    }
}

