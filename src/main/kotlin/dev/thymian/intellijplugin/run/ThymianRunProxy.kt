package dev.thymian.intellijplugin.run

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.microservices.oas.getOpenApi
import com.intellij.microservices.oas.squashOpenApiSpecifications
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.swagger.core.synthetic.generateOasDraft
import com.intellij.util.application
import dev.thymian.intellijplugin.cli.ActionListener
import dev.thymian.intellijplugin.cli.ThymianConnectorService
import dev.thymian.intellijplugin.models.ActionResultMessage
import dev.thymian.intellijplugin.models.EmitActionMessage
import dev.thymian.intellijplugin.models.Receiving
import java.util.concurrent.CompletableFuture

private class ProjectFilter(project: Project) : SearchScopeEndpointsFilter {
    override val contentSearchScope: GlobalSearchScope = GlobalSearchScope.allScope(project)
    override val transitiveSearchScope: GlobalSearchScope = GlobalSearchScope.allScope(project)
}

private fun getModuleFilters(project: Project) = ModuleManager.getInstance(project).modules
    .map { ModuleEndpointsFilter(it, false, false) }

internal class ThymianRunProxy<G : Any, E : Any>(
    private val project: Project,
    private val provider: EndpointsProvider<G, E>
) {
    val name = provider.presentation.title
    val smTestProxy = SMTestProxy(name, false, null)

    private lateinit var testData: List<DataContainer>
    val hasTestData by lazy { testData.isNotEmpty() }

    private val squashedSpecification: OpenApiSpecification by lazy {
        squashOpenApiSpecifications(testData.map { it.oas })
    }
    private val oasDraft by lazy {
        generateOasDraft(project.name, squashedSpecification)
    }

    init {
        smTestProxy.setStarted()
    }

    fun initialize() {
        smTestProxy.addStdOutput("loading endpoints\n")

        val projectFilter = ProjectFilter(project)

        val endPointGroups = getModuleFilters(project)
            .plusElement(projectFilter)
            .flatMap { provider.getEndpointGroups(project, it) }
            .distinct()

        testData = endPointGroups.flatMap { group ->
            provider.getEndpoints(group)
                .mapNotNull { endpoint ->
                    getOpenApi(provider, group, endpoint)
                        ?.let { DataContainer(group, endpoint, it) }
                }
        }
    }

    fun runTest(): CompletableFuture<Unit> {
        smTestProxy.addStdOutput("processing\n")

        val connection = project.getService(ThymianConnectorService::class.java)
        val result = CompletableFuture<Unit>()

        fun handleError(errorMessage: Receiving.ActionErrorMessage) {
            application.invokeLater {
                result.complete(Unit)
                smTestProxy.setFinished()
                smTestProxy.setTestFailed(errorMessage.error.message, null, true)
            }
        }

        fun handleLintingResult(lintResult: ActionResultMessage.HttpLinterLintStaticResponse) {
            val report = lintResult.payload
                .flatMap { it.reports }
                .joinToString("\n") { "${it.title} (${it.topic})\n${it.text}" }
            val isFailed = lintResult.payload.any { !it.valid }

            application.invokeLater {
                result.complete(Unit)
                smTestProxy.addStdOutput(report)
                smTestProxy.setFinished()
                if (isFailed) {
                    smTestProxy.setTestFailed("Linting failed", null, false)
                }
            }
        }

        fun handleTransformResult(transformResult: ActionResultMessage.OpenAPITransformResponse) {
            connection.sendAction(
                EmitActionMessage.HttpLinterLintStatic(
                    EmitActionMessage.HttpLinterLintStatic.Payload(transformResult.payload)
                ),
                ActionListener<ActionResultMessage.HttpLinterLintStaticResponse, _>(
                    onResult = ::handleLintingResult,
                    onError = ::handleError
                )
            )
        }

        connection.sendAction(
            EmitActionMessage.OpenAPITransform(
                EmitActionMessage.OpenAPITransform.Payload(oasDraft)
            ),
            ActionListener<ActionResultMessage.OpenAPITransformResponse, _>(
                onResult = ::handleTransformResult,
                onError = ::handleError
            )
        )

        return result
    }

    inner class DataContainer(
        val group: G,
        val endpoint: E,
        val oas: OpenApiSpecification
    )
}