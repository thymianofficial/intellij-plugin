package dev.thymian.intellijplugin.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.application


class ThymianRunState(
    val environment: ExecutionEnvironment,
    val configuration: ThymianRunConfiguration
) : RunProfileState {
    private val project = environment.project

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        val runProxies = if (configuration.runSettings.sortedEndpoints.isEmpty()) {
            getRunProxies()
        } else {
            getRunProxiesFromEndpoints(configuration.runSettings.sortedEndpoints)
        }

        val properties = SMTRunnerConsoleProperties(
            configuration,
            "Thymian",
            environment.executor
        )
        val console = SMTestRunnerConnectionUtil.createConsole(properties)
        val processHandler = ThymianRunProcessHandler(console.resultsViewer.testsRootNode, runProxies)
        console.attachToProcess(processHandler)

        val descriptor = RunContentDescriptor(
            console,
            processHandler,
            console.component,
            "Test Results",
            environment.executor.icon
        )

        return DefaultExecutionResult(
            console,
            processHandler,
            CloseAction(environment.executor, descriptor, configuration.project)
        )
    }

    private fun getRunProxies(): Sequence<ThymianRunProxy<*, *>> {
        return if (application.isDispatchThread) {
            // If things like the Spring controller tooling are not initialized yet, we must read the endpoints
            // wrapped in synchronous processing, because we trigger the initialization.
            // If we don't wrap it, things will crash.
            ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
                getRunProxiesFromProviders()
            }, "", true, null)
        } else {
            getRunProxiesFromProviders()
        }
    }

    private fun getRunProxiesFromProviders() = ReadAction.compute<Sequence<ThymianRunProxy<*, *>>, Throwable> {
        EndpointsProvider.getAvailableProviders(project)
            .filter { it.isAvailable() }
            .map { endpointProvider -> ThymianRunProxy(project, endpointProvider) }
    }

    private fun EndpointsProvider<*, *>.isAvailable(): Boolean {
        return getStatus(project) != EndpointsProvider.Status.UNAVAILABLE
    }

    private fun getRunProxiesFromEndpoints(endpoints: List<ThymianRunSettings.SortedEndpoints<*, *>>) =
        ReadAction.compute<Sequence<ThymianRunProxy<*, *>>, Throwable> {
            endpoints.map { ThymianRunProxy(project, it) }.asSequence()
        }
}

