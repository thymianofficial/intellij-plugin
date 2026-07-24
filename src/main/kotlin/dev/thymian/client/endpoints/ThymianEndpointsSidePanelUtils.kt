package dev.thymian.client.endpoints

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import dev.thymian.client.run.ThymianRunConfiguration
import dev.thymian.client.run.ThymianRunConfigurationType
import dev.thymian.client.run.ThymianRunSettings

fun runEndpointCheck(project: Project, items: List<EndpointsElementItem<*, *>>) = ReadAction.runBlocking<Throwable> {
    if (items.isEmpty()) {
        return@runBlocking
    }

    val configurationType = ThymianRunConfigurationType()
    val factory = configurationType.configurationFactories.first()

    val runManager = RunManager.getInstance(project)
    val runConfiguration = configurationType.createTemplateConfiguration(project) as ThymianRunConfiguration
    runConfiguration.runSettings = ThymianRunSettings(
        endpoints = items
    )

    val settings = runManager.createConfiguration(runConfiguration, factory)
    settings.isTemporary = true

    ProgramRunnerUtil.executeConfiguration(
        settings,
        DefaultRunExecutor.getRunExecutorInstance()
    )
}

fun hasUsableEndpointItems(selectedItems: Array<Any>) = hasUsableEndpointItems(selectedItems.toList())
fun hasUsableEndpointItems(selectedItems: List<Any>) = selectedItems.any { it is EndpointsElementItem<*, *> }

fun filterEndpointItems(selectedItems: Array<Any>) = filterEndpointItems(selectedItems.toList())
fun filterEndpointItems(selectedItems: List<Any>) = selectedItems.filterIsInstance<EndpointsElementItem<*, *>>()
