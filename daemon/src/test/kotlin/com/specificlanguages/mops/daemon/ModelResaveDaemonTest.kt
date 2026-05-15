package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.ModelResaveRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.io.TempDir

class ModelResaveDaemonTest {
    private val gson = Gson()

    @TempDir
    lateinit var tempDir: Path

    @Test
    @Disabled("Pending MPS API-backed model resave implementation")
    fun `model resave restores resolve attributes for mps-json structure model by name`() {
        val project = copyTestProject("mps-json")
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val original = model.readText()
        assertTrue(original.contains(""" resolve=""""), "fixture should contain resolve attributes")
        model.writeText(original.replace(Regex(""" resolve="[^"]*"""")) { "" })
        assertFalse(model.readText().contains(""" resolve=""""), "test setup should remove resolve attributes")

        val response = persistentServer(project).handle(
            gson.toJson(
                ModelResaveRequest(
                    protocolVersion = 1,
                    token = "secret",
                    modelTarget = "com.specificlanguages.json.structure",
                ),
            ),
        )

        assertImplementedResave(response)
        assertEquals(original, model.readText())
    }

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

        assertEquals(
            DaemonErrorResponse(
                type = "model-resave",
                protocolVersion = 1,
                errorCode = "NOT_IMPLEMENTED",
                message = "model resave is routed through the MPS daemon, but the MPS API resave implementation is not wired yet",
                logPath = "/state/daemon.log",
            ),
            response,
        )
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

        assertEquals(
            DaemonErrorResponse(
                type = "model-resave",
                protocolVersion = 1,
                errorCode = "INVALID_REQUEST",
                message = "modelTarget is required",
                logPath = "/state/daemon.log",
            ),
            response,
        )
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

    private fun persistentServer(projectPath: Path): PersistentDaemonServer =
        PersistentDaemonServer(
            environment = MpsEnvironmentState(
                projectPath = projectPath,
                mpsHome = Path.of("/mps"),
                ideaConfigDir = Path.of("/state/config"),
                ideaSystemDir = Path.of("/state/system"),
                logPath = Path.of("/state/daemon.log"),
            ),
            expectedToken = "secret",
        )

    private fun copyTestProject(name: String): Path {
        val source = Path.of(
            requireNotNull(javaClass.classLoader.getResource("test-projects/$name")) {
                "missing test project resource test-projects/$name"
            }.toURI(),
        )
        val target = tempDir.resolve(name).createDirectories()
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).pathString)
                if (Files.isDirectory(path)) {
                    destination.createDirectories()
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        return target
    }

    private fun assertImplementedResave(response: DaemonResponse): ModelResaveResponse =
        when (response) {
            is ModelResaveResponse -> response
            is DaemonErrorResponse -> {
                if (response.errorCode == "NOT_IMPLEMENTED") {
                    throw AssertionError("model resave is still not implemented: ${response.message}")
                }
                throw AssertionError("model resave failed with ${response.errorCode}: ${response.message}")
            }
            else -> throw AssertionError("model resave returned unexpected response type ${response.type}")
        }
}
