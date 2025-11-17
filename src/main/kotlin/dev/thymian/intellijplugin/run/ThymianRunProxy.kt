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

internal class ProjectFilter(project: Project) : SearchScopeEndpointsFilter {
    override val contentSearchScope: GlobalSearchScope = GlobalSearchScope.allScope(project)
    override val transitiveSearchScope: GlobalSearchScope = GlobalSearchScope.allScope(project)
}

internal fun getModuleFilters(project: Project) = ModuleManager.getInstance(project).modules
    .map { ModuleEndpointsFilter(it, false, false) }

internal class ThymianRunProxy<G : Any, E : Any>(
    private val project: Project,
    private val provider: EndpointsProvider<G, E>
) {
    val name = provider.presentation.title
    val smTestProxy = SMTestProxy(name, false, null)

    private lateinit var testData: List<DataContainer>
    val hasTestData get() = testData.isNotEmpty()

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

    inner class DataContainer(
        val group: G,
        val endpoint: E,
        val oas: OpenApiSpecification
    )
}