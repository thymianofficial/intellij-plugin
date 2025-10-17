package dev.thymian.intellijplugin.models

import com.qupaya.toggl.api.InstantSerializer
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
@Serializable
sealed interface Message {
    val payload: Payload

    @Serializable
    @SerialName("event")
    data class Event(override val payload: Payload.EventPayload) : Message

    @Serializable
    @SerialName("response")
    data class Response(override val payload: Payload.ResponsePayload) : Message

    @Serializable
    @SerialName("error")
    data class Error(override val payload: Payload.ErrorPayload) : Message
}

@Serializable
data class Init(val payload: InitPayload) {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type = "init"
}

@OptIn(ExperimentalTime::class)
sealed interface Payload {
    val id: String
    val name: String
    val timestamp: Instant
    val source: String

    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    @Serializable(NameSerializer::class)
    sealed interface EventPayload: Payload {
        @Serializable
        data class LoadFormatEvent(
            override val name: String,
            override val id: String,
            @Contextual
            @Serializable(with = InstantSerializer::class)
            override val timestamp: Instant,
            override val source: String
        ) : EventPayload

        @Serializable
        data class CoreReadyEvent(
            override val name: String,
            override val id: String,
            @Contextual
            @Serializable(with = InstantSerializer::class)
            override val timestamp: Instant,
            override val source: String
        ) : EventPayload
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    @Serializable(NameSerializer::class)
    sealed interface ResponsePayload : Payload {
        val correlationId: String
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    @Serializable
    data class ErrorPayload(
        override val id: String,
        override val name: String,
        val error: ThymianError,
        @Contextual
        @Serializable(with = InstantSerializer::class)
        override val timestamp: Instant,
        override val source: String,
        val correlationId: String?
    ) : Payload {
        @Serializable
        @JsonIgnoreUnknownKeys
        data class ThymianError(
            val name: String,
            val message: String? = null,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InitPayload(
    val name: String,
    val actions: Listeners,
    val events: Listeners,
) {
    @Serializable
    data class Listeners(val listensOn: List<String>)
}

object NameSerializer : JsonContentPolymorphicSerializer<Payload>(Payload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Payload> {
        val json = element.jsonObject
        val name = json.getValue("name").jsonPrimitive.content
        return when (name) {
            "core.ready" -> Payload.EventPayload.CoreReadyEvent.serializer()
            "core.load-format" -> Payload.EventPayload.LoadFormatEvent.serializer()
            else -> throw IllegalArgumentException("Unknown payload type: $name")
        }
    }
}