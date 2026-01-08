package dev.thymian.client.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import dev.thymian.client.ThymianBundle
import dev.thymian.client.ThymianIcons

class ThymianRunConfigurationType : SimpleConfigurationType(
    "ThymianConfigurationType",
    ThymianBundle.message("runConfiguration.name"),
    ThymianBundle.message("runConfiguration.description"),
    NotNullLazyValue.createConstantValue(ThymianIcons.Action)
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return ThymianRunConfiguration(project, this)
    }
}