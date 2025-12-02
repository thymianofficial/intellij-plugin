package dev.thymian.intellijplugin.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import dev.thymian.intellijplugin.ThymianIcons

class ThymianRunConfigurationType : SimpleConfigurationType(
    "ThymianConfigurationType",
    "Thymian",
    "Thymian command runner",
    NotNullLazyValue.createConstantValue(ThymianIcons.Action)
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return ThymianRunConfiguration(project, this)
    }
}