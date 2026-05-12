package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.ModelResaveRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelResaveDaemonTest {
    private val gson = Gson()

    @Test
    fun `persistent server accepts model resave request and returns explicit scaffold error`() {
        val server = persistentServer()

        val response = server.handle(
            gson.toJson(
                ModelResaveRequest(
                    protocolVersion = 1,
                    token = "secret",
                    modelTarget = "/project/models/main.mps",
                ),
            ),
        )

        val error = response as DaemonErrorResponse
        assertEquals("model-resave", error.type)
        assertEquals("error", error.status)
        assertEquals("NOT_IMPLEMENTED", error.errorCode)
        assertEquals("/state/daemon.log", error.logPath)
    }

    @Test
    fun `persistent server rejects model resave request without target`() {
        val server = persistentServer()

        val response = server.handle(
            gson.toJson(
                ModelResaveRequest(
                    protocolVersion = 1,
                    token = "secret",
                    modelTarget = null,
                ),
            ),
        )

        val error = response as DaemonErrorResponse
        assertEquals("model-resave", error.type)
        assertEquals("INVALID_REQUEST", error.errorCode)
        assertEquals("modelTarget is required", error.message)
        assertEquals("/state/daemon.log", error.logPath)
    }

    private fun persistentServer(): PersistentDaemonServer =
        PersistentDaemonServer(
            environment = MpsEnvironmentState(
                projectPath = Path.of("/project"),
                mpsHome = Path.of("/mps"),
                ideaConfigDir = Path.of("/state/config"),
                ideaSystemDir = Path.of("/state/system"),
                logPath = Path.of("/state/daemon.log"),
            ),
            expectedToken = "secret",
        )
}
