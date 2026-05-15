package com.specificlanguages.mops.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

const val ProtocolVersion = 1
val GsonCodec: Gson = GsonBuilder()
    .registerTypeAdapter(DaemonRequest::class.java, DaemonRequestJsonAdapter)
    .registerTypeAdapter(DaemonResponse::class.java, DaemonResponseJsonAdapter)
    .create()

private object DaemonRequestJsonAdapter : JsonDeserializer<DaemonRequest> {
    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): DaemonRequest {
        val message = messageObject(json)
        val targetType = when (val type = message.messageType("request")) {
            "ping" -> PingRequest::class.java
            "stop" -> StopRequest::class.java
            "model-resave" -> ModelResaveRequest::class.java
            else -> throw JsonParseException("unsupported request type $type")
        }
        return context.deserialize(message, targetType)
    }
}

private object DaemonResponseJsonAdapter : JsonDeserializer<DaemonResponse> {
    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): DaemonResponse {
        val message = messageObject(json)
        val targetType = if (message.stringField("status") == "error") {
            DaemonErrorResponse::class.java
        } else {
            when (val type = message.messageType("response")) {
                "ping" -> PingResponse::class.java
                "stop" -> StopResponse::class.java
                "model-resave" -> ModelResaveResponse::class.java
                "ready" -> ReadyMessage::class.java
                else -> throw JsonParseException("unsupported response type $type")
            }
        }
        return context.deserialize(message.withDefaultStatus(), targetType)
    }
}

private fun messageObject(json: JsonElement?): JsonObject {
    if (json == null || json.isJsonNull || !json.isJsonObject) {
        throw JsonParseException("message must be one JSON object")
    }
    return json.asJsonObject
}

private fun JsonObject.messageType(label: String): String {
    val type = stringField("type")
    if (type.isNullOrBlank()) {
        throw JsonParseException("$label type is required")
    }
    return type
}

private fun JsonObject.stringField(name: String): String? {
    val field = get(name) ?: return null
    if (field.isJsonNull) {
        return null
    }
    return field.asString
}

private fun JsonObject.withDefaultStatus(): JsonObject {
    if (has("status")) {
        return this
    }
    return deepCopy().apply {
        addProperty("status", "ok")
    }
}
