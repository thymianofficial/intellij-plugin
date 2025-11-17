package dev.thymian.intellijplugin.endpoints

import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsListItem
import com.intellij.microservices.endpoints.EndpointsSidePanel
import com.intellij.microservices.endpoints.EndpointsSidePanelProvider
import com.intellij.microservices.oas.getOpenApiSpecification
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.swagger.core.synthetic.generateOasDraft
import javax.swing.JComponent
import javax.swing.JLabel

class ThymianEndpointsSidePanel(val project: Project) : EndpointsSidePanel {
    override val title = "Thymian"
    override val component: JComponent = JLabel("Thymian Endpoints Side Panel")

    override suspend fun isAvailable(selectedItems: List<EndpointsListItem>): Boolean {
        print("isAvailable", selectedItems)
        return selectedItems.isNotEmpty() && selectedItems.any { it is EndpointsElementItem<*, *> }
    }

    override suspend fun update(selectedItems: List<EndpointsListItem>) {
        print("update", selectedItems)
    }

    override fun selected(selectedItems: List<EndpointsListItem>) {
        print("selected", selectedItems)
        for (item in selectedItems) {
            if (item !is EndpointsElementItem<*, *>) {
                continue
            }
        }
    }

    private fun print(where: String, selectedItems: List<EndpointsListItem>) {
        val content = ReadAction.compute<String, Throwable> {
            getOpenApiSpecification(selectedItems)
                ?.let { generateOasDraft(project.name, it) }
        }
        thisLogger().warn("$where:\n$content")
    }

    class Provider : EndpointsSidePanelProvider {
        override fun create(project: Project) = ThymianEndpointsSidePanel(project)
    }
}