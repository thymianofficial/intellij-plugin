package dev.thymian.client.cli

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/* *****************************************
 * Initialization
 * *****************************************/

@Serializable
data class Register(
    val name: String,
    val onActions: List<String>,
    val onEvents: List<String>,
) {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type = "register"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class RegisterResponse(
    val type: String,
    val ok: Boolean,
    val config: Configuration
) {
    @Serializable
    @JsonIgnoreUnknownKeys
    data class Configuration(
        val feature: Boolean? = null,
        val threshold: Int? = 0
    )
}

@Serializable
class Ready {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type: String = "ready"
}

/* *****************************************
 * Sending
 * *****************************************/

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("emit")
data class EmitEventMessage<T : Any>(
    val name: String,
    val payload: T,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("name")
sealed class EmitActionMessage {
    @OptIn(ExperimentalUuidApi::class)
    val id: String = Uuid.random().toString()

    @EncodeDefault
    val type = "emitAction"
    open var options: Options? = null

    /**
     * Options for action execution.
     *
     * @param strategy The strategy to use for the action: "first", "collect", "deep-merge".
     * @param timeout The timeout in milliseconds for waiting for replies.
     */
    @Serializable
    data class Options(
        val strategy: String,
        val timeout: Int
    )

    @Serializable
    @SerialName("core.workflow.lint")
    data class CoreWorkflowLint(
        val payload: Payload
    ) : EmitActionMessage() {

        @Serializable
        data class Payload(
            val specification: List<Specification>,
            val rules: List<String> = emptyList(),
            val validateSpecs: Boolean = false,
        )

        @Serializable
        data class Specification(
            val type: String,
            val location: String,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("actionReply")
data class EmitActionResultMessage<T : Any>(
    val correlationId: String,
    val name: String,
    val payload: T,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("actionError")
data class EmitActionErrorMessage(
    val correlationId: String,
    val name: String,
    val error: ErrorPayload,
)

/* *****************************************
 * Receiving
 * *****************************************/

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Receiving {
    @Serializable
    @SerialName("event")
    @JsonIgnoreUnknownKeys
    data class EventMessage(
        val name: String,
    ) : Receiving

    @Serializable
    @SerialName("action")
    @JsonIgnoreUnknownKeys
    data class ActionMessage(
        val id: String,
        val name: String,
    ) : Receiving


    @Serializable
    @SerialName("emitActionResult")
    @JsonIgnoreUnknownKeys
    data class ActionResultMessageWrapper(
        val correlationId: String,
        val name: String,
        val payload: JsonElement,
    ) : Receiving {
        fun toTypedMessage(): ActionResultMessage<*> = when (name) {
            "core.workflow.lint" -> ActionResultMessage.CoreWorkflowLintResponse(
                correlationId = correlationId,
                name = name,
                payload = Json.decodeFromJsonElement(payload)
            )

            else -> throw IllegalArgumentException("Unknown action result type: $name")
        }
    }

    @Serializable
    @SerialName("emitActionError")
    @JsonIgnoreUnknownKeys
    data class ActionErrorMessage(
        val correlationId: String,
        val name: String,
        val error: ErrorPayload,
    ) : Receiving
}


sealed interface ActionResultMessage<T : Any> {
    val correlationId: String
    val name: String
    val payload: T

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonIgnoreUnknownKeys
    data class CoreWorkflowLintResponse(
        override val correlationId: String,
        override val name: String,
        override val payload: Report,
    ) : ActionResultMessage<Report>
}

/* *****************************************
 * Report (core.workflow.lint response payload)
 * *****************************************/

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Report(
    val reportId: String,
    val createdAt: String,
    val runs: List<ToolRun>,
    val thymianFormat: Map<String, SerializedThymianFormat>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ToolRun(
    val runId: String,
    val runType: String,
    val runAt: String,
    val executions: List<Execution>? = null,
    val thymianFormatVersion: String? = null,
    val rules: List<RuleDescriptor>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Execution(
    val kind: String,
    val ruleId: String? = null,
    val status: ExecutionStatus,
    val findings: List<Finding>? = null,
    val location: Location? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ExecutionStatus(
    val kind: String,
    val reason: String? = null,
    val severity: String? = null,
    val durationMilliseconds: Double? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Finding(
    val id: String,
    val kind: String,
    val title: String,
    val message: FindingMessage? = null,
    val expected: JsonElement? = null,
    val actual: JsonElement? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class FindingMessage(
    val text: String,
    val markdown: String? = null,
)

/**
 * Metadata describing a rule known to a tool run. Mirrors `RuleDescriptor` from
 * `packages/core/src/report/report.ts`; only the fields the renderer needs are modeled.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class RuleDescriptor(
    val id: String,
    val name: String? = null,
    val summary: FindingMessage? = null,
    val description: FindingMessage? = null,
    val severity: String? = null,
)

/**
 * Subject/location of an execution. Mirrors the `Location` discriminated union from
 * `packages/core/src/report/report.ts`. `thymianFormat` locations are references into the
 * corresponding entry of `Report.thymianFormat` and must be resolved via `LocationFormat`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Location {
    @Serializable
    @SerialName("thymianFormat")
    @JsonIgnoreUnknownKeys
    data class ThymianFormatLocation(
        val elementType: String,
        val elementId: String,
        val pointer: String = "",
    ) : Location

    @Serializable
    @SerialName("url")
    @JsonIgnoreUnknownKeys
    data class UrlLocation(
        val url: String,
    ) : Location

    @Serializable
    @SerialName("file")
    @JsonIgnoreUnknownKeys
    data class FileLocation(
        val path: String,
        val line: Int? = null,
        val column: Int? = null,
    ) : Location

    @Serializable
    @SerialName("custom")
    @JsonIgnoreUnknownKeys
    data class CustomLocation(
        val value: String,
    ) : Location
}

/* *****************************************
 * Serialized ThymianFormat graph (Report.thymianFormat entries)
 *
 * Mirrors the graphology `SerializedGraph` shape produced by `ThymianFormat.export()`
 * (`packages/core/src/format/thymian-format.ts`): a flat list of nodes/edges keyed by id.
 * Only the node/edge attributes needed to resolve `thymianFormat` locations to HTTP
 * request/response strings are modeled; everything else is ignored on decode.
 * *****************************************/

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class SerializedThymianFormat(
    val nodes: List<SerializedNode> = emptyList(),
    val edges: List<SerializedEdge> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class SerializedNode(
    val key: String,
    val attributes: GraphNodeAttributes,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class SerializedEdge(
    val key: String,
    val source: String,
    val target: String,
    val attributes: GraphEdgeAttributes? = null,
)

/** `ThymianNode` attributes, restricted to `http-request`/`http-response` fields. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class GraphNodeAttributes(
    val type: String? = null,
    val method: String? = null,
    val path: String? = null,
    val mediaType: String? = null,
    val statusCode: Int? = null,
)

/** `ThymianEdge` attributes, e.g. `http-transaction`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class GraphEdgeAttributes(
    val type: String? = null,
)

/* *****************************************
 * Payloads
 * *****************************************/

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ErrorPayload(
    val name: String? = null,
    val message: String,
)
