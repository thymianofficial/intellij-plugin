package dev.thymian.intellijplugin.cli

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

@Serializable
data class RegisterResponse(
    val type: String,
    val ok: Boolean,
    val config: Configuration
) {
    @Serializable
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
    open val options: Options? = null

    /**
     * Options for action execution.
     *
     * @param strategy The strategy to use for the action: "first", "collect", "deep-merge".
     * @param timeout The timeout in milliseconds for waiting for replies.
     */
    @Serializable
    data class Options(
        val strategy: String? = "first",
        val timeout: Int
    )

    @Serializable
    @SerialName("openapi.transform")
    data class OpenAPITransform(
        val payload: Payload
    ) : EmitActionMessage() {

        @Serializable
        class Payload(val content: String)
    }

    @Serializable
    @SerialName("http-linter.lint-static")
    data class HttpLinterLintStatic(
        val payload: Payload
    ) : EmitActionMessage() {

        @Serializable
        class Payload(val format: JsonElement)
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
    data class ActionResultMessageWrapper(
        val correlationId: String,
        val name: String,
        val payload: JsonElement,
    ) : Receiving {
        fun toTypedMessage(): ActionResultMessage<*> = when (name) {
            "openapi.transform" -> ActionResultMessage.OpenAPITransformResponse(
                correlationId = correlationId,
                name = name,
                payload = payload
            )

            "http-linter.lint-static" -> ActionResultMessage.HttpLinterLintStaticResponse(
                correlationId = correlationId,
                name = name,
                payload = Json.decodeFromJsonElement(payload)
            )

            else -> throw IllegalArgumentException("Unknown action result type: $name")
        }
    }

    @Serializable
    @SerialName("emitActionError")
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

    @Serializable
    data class OpenAPITransformResponse(
        override val correlationId: String,
        override val name: String,
        override val payload: JsonElement,
    ) : ActionResultMessage<JsonElement>

    @Serializable
    data class HttpLinterLintStaticResponse(
        override val correlationId: String,
        override val name: String,
        override val payload: List<Payload>,
    ) : ActionResultMessage<List<HttpLinterLintStaticResponse.Payload>> {
        @Serializable
        data class Payload(
            val reports: List<ThymianReport>,
            val valid: Boolean,
        )

        @OptIn(ExperimentalSerializationApi::class)
        @Serializable
        @JsonIgnoreUnknownKeys
        data class ThymianReport(
            val topic: String,
            val subTopic: String? = null,
            val title: String,
            val text: String,
            val isProblem: Boolean,
        )
    }
}

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
