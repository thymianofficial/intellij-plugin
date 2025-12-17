package dev.thymian.intellijplugin.run

import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsProvider

class ThymianRunSettings(
    endpoints: List<EndpointsElementItem<*, *>> = emptyList()
) {
    val sortedEndpoints: List<SortedEndpoints<*, *>> =
        endpoints.groupBy { it.provider }
            .map { (provider, items) ->
                @Suppress("UNCHECKED_CAST")
                SortedEndpoints(
                    provider as EndpointsProvider<Any, Any>,
                    items as List<EndpointsElementItem<Any, Any>>
                )
            }

    data class SortedEndpoints<G : Any, E : Any>(
        val provider: EndpointsProvider<G, E>,
        val endpoints: List<EndpointsElementItem<G, E>>
    ) {
        val pairedEndpoints: List<Pair<G, E>> get() = endpoints.map { it.group to it.endpoint }
    }
}