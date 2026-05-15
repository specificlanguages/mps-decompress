package com.specificlanguages.mops.protocol

import com.google.gson.JsonParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DaemonProtocolJsonTest {
    @Test
    fun `request JSON decodes to concrete daemon request messages`() {
        assertEquals(
            PingRequest(protocolVersion = 1, token = "secret"),
            GsonCodec.fromJson(
                """{"type":"ping","protocolVersion":1,"token":"secret"}""",
                DaemonRequest::class.java,
            ),
        )
        assertEquals(
            StopRequest(protocolVersion = 1, token = "secret"),
            GsonCodec.fromJson(
                """{"type":"stop","protocolVersion":1,"token":"secret"}""",
                DaemonRequest::class.java,
            ),
        )
        assertEquals(
            ModelResaveRequest(protocolVersion = 1, token = "secret", modelTarget = "/project/models/main.mps"),
            GsonCodec.fromJson(
                """{"type":"model-resave","protocolVersion":1,"token":"secret","modelTarget":"/project/models/main.mps"}""",
                DaemonRequest::class.java,
            ),
        )
    }

    @Test
    fun `response JSON decodes to concrete daemon response messages`() {
        assertEquals(
            ReadyMessage(protocolVersion = 999, port = 3210),
            GsonCodec.fromJson(
                """{"type":"ready","protocolVersion":999,"port":3210}""",
                DaemonResponse::class.java,
            ),
        )
        assertEquals(
            DaemonErrorResponse(
                type = "model-resave",
                protocolVersion = 1,
                errorCode = "NOT_IMPLEMENTED",
                message = "not wired yet",
                logPath = "/state/daemon.log",
            ),
            GsonCodec.fromJson(
                """{"type":"model-resave","status":"error","protocolVersion":1,"errorCode":"NOT_IMPLEMENTED","message":"not wired yet","logPath":"/state/daemon.log"}""",
                DaemonResponse::class.java,
            ),
        )
    }

    @Test
    fun `message adapters require a type discriminator`() {
        val exception = assertFailsWith<JsonParseException> {
            GsonCodec.fromJson("""{"protocolVersion":1,"token":"secret"}""", DaemonRequest::class.java)
        }

        assertEquals("request type is required", exception.message)
    }
}
