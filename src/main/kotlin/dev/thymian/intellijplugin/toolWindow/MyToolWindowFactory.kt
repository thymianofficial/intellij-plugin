package dev.thymian.intellijplugin.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import dev.thymian.intellijplugin.ThymianBundle
import dev.thymian.intellijplugin.cli.ThymianConnectorService
import dev.thymian.intellijplugin.services.MyProjectService
import javax.swing.JButton

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()
        private val thymian = toolWindow.project.service<ThymianConnectorService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(ThymianBundle.message("randomLabel", "?"))

            add(label)
            add(JButton(ThymianBundle.message("shuffle")).apply {
                addActionListener {
                    label.text = ThymianBundle.message("randomLabel", service.getRandomNumber())
                    println(thymian)
                }
            })
        }
    }
}