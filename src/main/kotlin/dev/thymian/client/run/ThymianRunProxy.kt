package dev.thymian.client.run

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.microservices.endpoints.API_DEFINITION_TYPE
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.microservices.oas.getOpenApi
import com.intellij.microservices.oas.squashOpenApiSpecifications
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.swagger.core.synthetic.generateOasDraft
import com.intellij.util.application
import dev.thymian.client.cli.*
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

    constructor(project: Project, sortedEndpoint: ThymianRunSettings.SortedEndpoints<G, E>)
            : this(project, sortedEndpoint.provider) {
        testData = sortedEndpoint.pairedEndpoints
            .map { (group, endpoint) -> DataContainer(group, endpoint) }
            .groupBy { it.file }
    }

    init {
        smTestProxy.setStarted()
    }

    fun initialize() {
        if (this::testData.isInitialized) {
            return
        }

        smTestProxy.addStdOutput("Loading endpoints\n")

        val projectFilter = ProjectFilter(project)

        val endPointGroups = getModuleFilters(project)
            .plusElement(projectFilter)
            .flatMap { provider.getEndpointGroups(project, it) }
            .distinct()

        testData = endPointGroups.flatMap { group ->
            provider.getEndpoints(group)
                .map { endpoint -> DataContainer(group, endpoint) }
        }
            .groupBy { it.file }
    }

    fun runTest(thymianCLI: ThymianCLI): CompletableFuture<Unit> {
        smTestProxy.addStdOutput("Processing\n")
        val testSets = testData.map { (file, data) ->
            val dataProxy = SMTestProxy(file?.name, false, null)
            application.invokeLater {
                smTestProxy.addChild(dataProxy)
                dataProxy.setStarted()
            }
            val allSpecs = data.mapNotNull { it.oas }
            val squashedSpecs = if (allSpecs.isEmpty()) null else squashOpenApiSpecifications(allSpecs)
            TestSet(file, dataProxy, squashedSpecs)
        }

        return testSets.fold(CompletableFuture.completedFuture(Unit)) { acc, testSet ->
            acc.thenCompose { runTestInternal(thymianCLI, testSet) }
        }.thenApply {
            smTestProxy.setFinished()
        }
    }

    private fun runTestInternal(thymianCLI: ThymianCLI, testSet: TestSet): CompletableFuture<Unit> {
        val oasDraft = when {
            provider.endpointType == API_DEFINITION_TYPE -> testSet.file?.containingFile?.text
            testSet.specification != null -> generateOasDraft(project.name, testSet.specification)
            else -> null
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
                .joinToString("\n\n") { "${it.severity}: ${it.title} (${it.category})\n${it.summary}" }
            val isFailed = lintResult.payload.any { !it.valid }

            application.invokeLater {
                result.complete(Unit)
                with(testSet.smTestProxy) {
                    addStdOutput(report)
                    addStdOutput("Done\n")
                    setFinished()
                    if (isFailed) {
                        setTestFailed("Linting found issues", null, false)
                        smTestProxy.setTestFailed(null, null, false)
                    }
                }
            }
        }

        fun lintTransformResult(transformResult: ActionResultMessage.OpenAPITransformResponse) {
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

        val transformMessage = EmitActionMessage.OpenAPITransform(
            EmitActionMessage.OpenAPITransform.Payload(oasDraft)
        )
        transformMessage.options = EmitActionMessage.Options(timeout = 10000, strategy = "first")
        thymianCLI.sendAction(
            transformMessage,
            ActionListener<ActionResultMessage.OpenAPITransformResponse, _>(
                onResult = ::lintTransformResult,
                onError = ::handleError
            )
        )

        return result
    }

    private inner class DataContainer(
        val group: G,
        val endpoint: E
    ) {
        val oas: OpenApiSpecification? = ReadAction.compute<OpenApiSpecification?, Throwable> {
            getOpenApi(provider, group, endpoint)
        }

        private val element get() = provider.getNavigationElement(group, endpoint)
        val file by lazy { element?.containingFile }
    }

    private class TestSet(
        val file: PsiFile?,
        val smTestProxy: SMTestProxy,
        val specification: OpenApiSpecification?
    )
}
