package dev.thymian.intellijplugin.endpoints

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import dev.thymian.intellijplugin.ThymianBundle

class ThymianEndpointsRunCheckAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val items = e.dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS)
        val usableEndpoints = items?.let { filterEndpointItems(it) } ?: emptyList()

        if (usableEndpoints.isEmpty()) {
            return
        }

        runEndpointCheck(project, usableEndpoints)
    }

    override fun update(e: AnActionEvent) {
        val items = e.dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS)

        e.presentation.text = ThymianBundle.message("endpoints.sidepanel.action")
        e.presentation.isEnabled = items?.let { hasUsableEndpointItems(it) } ?: false
    }
}