package dev.thymian.intellijplugin.endpoints

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsListItem
import com.intellij.microservices.endpoints.EndpointsSidePanel
import com.intellij.microservices.endpoints.EndpointsSidePanelProvider
import com.intellij.microservices.oas.getOpenApiSpecification
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.swagger.core.synthetic.generateOasDraft
import com.intellij.ui.dsl.builder.panel
import dev.thymian.intellijplugin.ThymianBundle
import dev.thymian.intellijplugin.run.ThymianRunConfiguration
import dev.thymian.intellijplugin.run.ThymianRunConfigurationType
import dev.thymian.intellijplugin.run.ThymianRunSettings
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ThymianEndpointsSidePanel(val project: Project) : EndpointsSidePanel {
    override val title = ThymianBundle.message("title")
    private var currentSelectedItems: List<EndpointsElementItem<*, *>> = emptyList()
    private lateinit var runButton: JButton
    override val component: JComponent = panel {
        row {
            text("Thymian endpoint check")
        }
        row {
            val btn = button("Run check") {
                val configurationType =
                    ConfigurationTypeUtil.findConfigurationType(ThymianRunConfigurationType::class.java)
                val factory = configurationType.configurationFactories.first()

                val runManager = RunManager.getInstance(project)
                val runConfiguration = ThymianRunConfiguration(project, factory).apply {
                    runSettings = ThymianRunSettings(
                        endpoints = currentSelectedItems
                    )
                }
                val settings = runManager.createConfiguration(runConfiguration, factory)
                settings.isTemporary = true

                ProgramRunnerUtil.executeConfiguration(
                    settings,
                    DefaultRunExecutor.getRunExecutorInstance()
                )
            }
            runButton = btn.component
            btn.enabled(false)
        }
    }

    override suspend fun isAvailable(selectedItems: List<EndpointsListItem>): Boolean {
        return selectedItems.any { it is EndpointsElementItem<*, *> }
    }

    override suspend fun update(selectedItems: List<EndpointsListItem>) {
        currentSelectedItems = selectedItems.filterIsInstance<EndpointsElementItem<*, *>>()
        setRunButtonEnabled(currentSelectedItems.isNotEmpty())
    }

    override fun selected(selectedItems: List<EndpointsListItem>) {
        currentSelectedItems = selectedItems.filterIsInstance<EndpointsElementItem<*, *>>()
        setRunButtonEnabled(currentSelectedItems.isNotEmpty())
    }

    private fun print(where: String, selectedItems: List<EndpointsListItem>) {
        val content = ReadAction.compute<String, Throwable> {
            getOpenApiSpecification(selectedItems)
                ?.let { generateOasDraft(project.name, it) }
        }
        thisLogger().warn("$where:\n$content")
    }

    private fun setRunButtonEnabled(enabled: Boolean) = SwingUtilities.invokeLater {
        if (this::runButton.isInitialized) {
            runButton.isEnabled = enabled
        }
    }

    class Provider : EndpointsSidePanelProvider {
        override fun create(project: Project) = ThymianEndpointsSidePanel(project)
    }
}