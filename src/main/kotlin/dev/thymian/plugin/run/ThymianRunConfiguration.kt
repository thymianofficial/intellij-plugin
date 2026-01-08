package dev.thymian.plugin.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import dev.thymian.plugin.ThymianBundle
import org.jdom.Element

class ThymianRunConfiguration(project: Project, factory: ConfigurationFactory) :
    LocatableConfigurationBase<RunProfileState>(project, factory, ThymianBundle.message("runConfiguration.name")) {

    internal var runSettings = ThymianRunSettings()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return ThymianRunState(environment, this)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return ThymianRunConfigurationEditor()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
    }

    override fun suggestedName(): String {
        if (runSettings.sortedEndpoints.isEmpty()) {
            return ""
        }

        return runSettings.sortedEndpoints.joinToString(";") { (provider, items) ->
            "${provider.presentation.title}(${
                items.mapNotNull {
                    it.getUrlTargetInfos()?.firstOrNull()?.path?.getPresentation()
                }.joinToString(",")
            })"
        }
    }
}


fun Element.writeString(name: String, value: String) {
    val opt = Element("option")
    opt.setAttribute("name", name)
    opt.setAttribute("value", value)
    addContent(opt)
}

fun Element.readString(name: String): String? =
    children
        .find { it.name == "option" && it.getAttributeValue("name") == name }
        ?.getAttributeValue("value")