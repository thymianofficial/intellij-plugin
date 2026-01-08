package dev.thymian.client.endpoints

import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsListItem
import com.intellij.microservices.endpoints.EndpointsSidePanel
import com.intellij.microservices.endpoints.EndpointsSidePanelProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import dev.thymian.client.ThymianBundle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ThymianEndpointsSidePanel(val project: Project) : EndpointsSidePanel {
    override val title = ThymianBundle.message("endpoints.sidepanel.title")
    private var currentSelectedItems = emptyList<EndpointsElementItem<*, *>>()

    private lateinit var runButton: JButton
    override val component: JComponent = panel {
        row {
            text(ThymianBundle.message("endpoints.sidepanel.info"))
        }
        row {
            val btn = button(ThymianBundle.message("endpoints.sidepanel.runCheck")) {
                runEndpointCheck(project, currentSelectedItems)
            }
            runButton = btn.component
            btn.enabled(false)
        }
    }

    override suspend fun isAvailable(selectedItems: List<EndpointsListItem>) = hasUsableEndpointItems(selectedItems)

    override suspend fun update(selectedItems: List<EndpointsListItem>) {
        currentSelectedItems = filterEndpointItems(selectedItems)
        setRunButtonEnabled(currentSelectedItems.isNotEmpty())
    }

    override fun selected(selectedItems: List<EndpointsListItem>) {
        currentSelectedItems = filterEndpointItems(selectedItems)
        setRunButtonEnabled(currentSelectedItems.isNotEmpty())
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