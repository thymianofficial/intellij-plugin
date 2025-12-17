package dev.thymian.intellijplugin.run

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.microservices.endpoints.API_DEFINITION_TYPE
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.microservices.oas.getOpenApi
import com.intellij.microservices.oas.squashOpenApiSpecifications
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.swagger.core.synthetic.generateOasDraft
import com.intellij.util.application
import dev.thymian.intellijplugin.cli.*
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
    val smTestProxy = SMTestProxy(name, true, null)

    private lateinit var testData: Map<PsiFile?, List<DataContainer>>
    val hasTestData by lazy { testData.isNotEmpty() }

    constructor(project: Project, sortedEndpoint: ThymianRunSettings.SortedEndpoints<G, E>)
            : this(project, sortedEndpoint.provider) {
        testData = sortedEndpoint.pairedEndpoints.mapNotNull { (group, endpoint) ->
            getOpenApi(provider, group, endpoint)
                ?.let { DataContainer(group, endpoint, it) }
        }
            .groupBy { it.file }
    }

    init {
        smTestProxy.setStarted()
    }

    fun initialize() {
        if (this::testData.isInitialized) {
            return
        }

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
            .groupBy { it.file }
    }

    fun runTest(thymianCLI: ThymianCLI): CompletableFuture<Unit> {
        smTestProxy.addStdOutput("processing\n")
        val testSets = testData.map { (file, data) ->
            val dataProxy = SMTestProxy(file?.name, false, null)
            application.invokeLater {
                smTestProxy.addChild(dataProxy)
                dataProxy.setStarted()
            }
            val squashedSpecs = squashOpenApiSpecifications(data.map { it.oas })
            TestSet(file, dataProxy, squashedSpecs)
        }

        return testSets.fold(CompletableFuture.completedFuture(Unit)) { acc, testSet ->
            acc.thenCompose { runTestInternal(thymianCLI, testSet) }
        }.thenApply {
            smTestProxy.setFinished()
        }
    }

    private fun runTestInternal(thymianCLI: ThymianCLI, testSet: TestSet): CompletableFuture<Unit> {
        val oasDraft = if (provider.endpointType == API_DEFINITION_TYPE) {
            testSet.file?.containingFile?.text
        } else {
            generateOasDraft(project.name, testSet.specification)
        }

        if (oasDraft == null) {
            application.invokeLater {
                testSet.smTestProxy.setFinished()
                testSet.smTestProxy.setTestFailed("Unable to generate specification draft", null, true)
            }
            return CompletableFuture.completedFuture(Unit)
        }

        val result = CompletableFuture<Unit>()

        fun handleError(errorMessage: Receiving.ActionErrorMessage) {
            application.invokeLater {
                result.complete(Unit)
                testSet.smTestProxy.setFinished()
                testSet.smTestProxy.setTestFailed(errorMessage.error.message, null, true)
            }
        }

        fun handleLintingResult(lintResult: ActionResultMessage.HttpLinterLintStaticResponse) {
            val report = lintResult.payload
                .flatMap { it.reports }
                .joinToString("\n") { "${it.title} (${it.topic})\n${it.text}" }
            val isFailed = lintResult.payload.any { !it.valid }

            application.invokeLater {
                result.complete(Unit)
                testSet.smTestProxy.addStdOutput(report)
                testSet.smTestProxy.setFinished()
                if (isFailed) {
                    testSet.smTestProxy.setTestFailed("Linting failed", null, false)
                }
            }
        }

        fun forwardTransformResult(transformResult: ActionResultMessage.OpenAPITransformResponse) {
            thymianCLI.sendAction(
                EmitActionMessage.HttpLinterLintStatic(
                    EmitActionMessage.HttpLinterLintStatic.Payload(transformResult.payload)
                ),
                ActionListener<ActionResultMessage.HttpLinterLintStaticResponse, _>(
                    onResult = ::handleLintingResult,
                    onError = ::handleError
                )
            )
        }

        thymianCLI.sendAction(
            EmitActionMessage.OpenAPITransform(
                EmitActionMessage.OpenAPITransform.Payload(oasDraft)
            ),
            ActionListener<ActionResultMessage.OpenAPITransformResponse, _>(
                onResult = ::forwardTransformResult,
                onError = ::handleError
            )
        )

        return result
    }

    private inner class DataContainer(
        val group: G,
        val endpoint: E,
        val oas: OpenApiSpecification
    ) {
        private val element get() = provider.getNavigationElement(group, endpoint)
        val file by lazy { element?.containingFile }
    }

    private class TestSet(
        val file: PsiFile?,
        val smTestProxy: SMTestProxy,
        val specification: OpenApiSpecification
    )
}
