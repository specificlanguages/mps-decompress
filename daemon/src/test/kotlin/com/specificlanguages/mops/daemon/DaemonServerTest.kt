package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import com.specificlanguages.mops.protocol.DaemonControlRequest
import com.specificlanguages.mops.protocol.DaemonControlResponse
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ReadyMessage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DaemonServerTest {
    private val gson = Gson()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `successful ping returns project and mps home`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 1, token = "secret"),
        )

        assertEquals("ok", response.status)
        assertEquals(1, response.protocolVersion)
        assertEquals("/project", response.projectPath)
        assertEquals("/mps", response.mpsHome)
        assertEquals(true, response.environmentReady)
        assertEquals("/state/daemon.log", response.logPath)
    }

    @Test
    fun `token mismatch returns structured error`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 1, token = "wrong"),
        )

        assertEquals("error", response.status)
        assertEquals("TOKEN_MISMATCH", response.errorCode)
    }

    @Test
    fun `protocol mismatch returns structured error`() {
        val response = exchange(
            request = PingRequest(type = "ping", protocolVersion = 999, token = "secret"),
        )

        assertEquals("error", response.status)
        assertEquals("PROTOCOL_MISMATCH", response.errorCode)
    }

    @Test
    fun `runtime bootstrap creates isolated idea directories and log outside project`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2024.1\n")
        mpsHome.resolve("plugins").createDirectory()
        val state = tempDir.resolve("state")
        val bootstrap = MpsRuntimeBootstrap(
            projectPath = project,
            mpsHome = mpsHome,
            ideaConfigDir = state.resolve("config"),
            ideaSystemDir = state.resolve("system"),
            logPath = state.resolve("logs/daemon.log"),
        )

        val environment = bootstrap.initialize()

        assertTrue(environment.ideaConfigDir.exists())
        assertTrue(environment.ideaSystemDir.exists())
        assertTrue(environment.logPath.exists())
        assertTrue(!environment.ideaConfigDir.pathString.startsWith(project.pathString))
        assertTrue(java.nio.file.Files.readString(environment.logPath).contains("idea.config.path"))
    }

    @Test
    fun `runtime bootstrap opens project through configured IDEA project session`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2024.1\n")
        val plugin = mpsHome.resolve("plugins/mps-example/META-INF").createDirectories()
        java.nio.file.Files.writeString(plugin.resolve("plugin.xml"), "<idea-plugin><id>com.example.plugin</id></idea-plugin>")
        val opener = RecordingProjectSessionOpener()
        val bootstrap = MpsRuntimeBootstrap(
            projectPath = project,
            mpsHome = mpsHome,
            ideaConfigDir = tempDir.resolve("state/config"),
            ideaSystemDir = tempDir.resolve("state/system"),
            logPath = tempDir.resolve("state/logs/daemon.log"),
            projectSessionOpener = opener,
        )

        bootstrap.withLoadedProject { environment ->
            assertEquals(project, environment.projectPath)
        }

        val config = opener.config ?: error("project session was not opened")
        assertEquals(project, config.projectPath)
        assertEquals(mpsHome.resolve("plugins"), config.pluginRoot)
        assertEquals("2024.1", config.buildNumber)
        assertEquals(listOf("com.example.plugin"), config.plugins.map { it.id })
    }

    @Test
    fun `runtime bootstrap logs startup failures`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val missingMpsHome = tempDir.resolve("missing-mps")
        val logPath = tempDir.resolve("state/logs/daemon.log")
        val bootstrap = MpsRuntimeBootstrap(
            projectPath = project,
            mpsHome = missingMpsHome,
            ideaConfigDir = tempDir.resolve("state/config"),
            ideaSystemDir = tempDir.resolve("state/system"),
            logPath = logPath,
        )

        val exception = assertFailsWith<IllegalStateException> {
            bootstrap.initialize()
        }
        bootstrap.log("startup failed: ${exception.message}")

        assertTrue(java.nio.file.Files.readString(logPath).contains("startup failed"))
        assertTrue(java.nio.file.Files.readString(logPath).contains("MPS home is not a directory"))
    }

    @Test
    fun `persistent server accepts multiple pings before graceful stop`() {
        val latch = CountDownLatch(1)
        lateinit var ready: ReadyMessage
        val server = PersistentDaemonServer(
            environment = MpsEnvironmentState(
                projectPath = Path.of("/project"),
                mpsHome = Path.of("/mps"),
                ideaConfigDir = Path.of("/state/config"),
                ideaSystemDir = Path.of("/state/system"),
                logPath = Path.of("/state/daemon.log"),
            ),
            expectedToken = "secret",
        )
        val thread = Thread {
            server.serve {
                ready = it
                latch.countDown()
            }
        }
        thread.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "server did not bind a socket")
        assertEquals("ok", exchange(ready.port, PingRequest(type = "ping", protocolVersion = 1, token = "secret")).status)
        assertEquals("ok", exchange(ready.port, PingRequest(type = "ping", protocolVersion = 1, token = "secret")).status)
        assertEquals("ok", exchangeControl(ready.port, DaemonControlRequest(type = "stop", protocolVersion = 1, token = "secret")).status)
        thread.join(5_000)

        assertTrue(!thread.isAlive, "server did not exit after stop")
    }

    @Test
    fun `persistent server reports invalid request when request type is missing`() {
        val server = persistentServer()

        val response = server.handle("{}")

        val error = response as DaemonErrorResponse
        assertEquals("error", error.type)
        assertEquals("error", error.status)
        assertEquals("INVALID_REQUEST", error.errorCode)
        assertEquals("request type is required", error.message)
    }

    @Test
    fun `persistent server reports invalid request when request type is null`() {
        val server = persistentServer()

        val response = server.handle("""{"type":null,"protocolVersion":1,"token":"secret"}""")

        val error = response as DaemonErrorResponse
        assertEquals("error", error.type)
        assertEquals("error", error.status)
        assertEquals("INVALID_REQUEST", error.errorCode)
    }

    @Test
    fun `plugin scanner detects plugins under MPS plugins root`() {
        val pluginsRoot = tempDir.resolve("mps/plugins").createDirectories()
        val pluginMeta = pluginsRoot.resolve("mps-example/META-INF").createDirectories()
        java.nio.file.Files.writeString(
            pluginMeta.resolve("plugin.xml"),
            "<idea-plugin><id>com.example.plugin</id></idea-plugin>",
        )

        val plugins = PluginScanner.findPlugins(pluginsRoot)

        assertEquals(listOf("com.example.plugin"), plugins.map { it.id })
        assertEquals(pluginsRoot.resolve("mps-example").toAbsolutePath().normalize(), plugins.single().path)
    }

    private fun exchange(request: PingRequest): PingResponse {
        val latch = CountDownLatch(1)
        lateinit var ready: ReadyMessage
        val server = SingleUsePingServer(
            environment = MpsEnvironmentState(
                projectPath = Path.of("/project"),
                mpsHome = Path.of("/mps"),
                ideaConfigDir = Path.of("/state/config"),
                ideaSystemDir = Path.of("/state/system"),
                logPath = Path.of("/state/daemon.log"),
            ),
            expectedToken = "secret",
        )
        val thread = Thread {
            server.serveOnce {
                ready = it
                latch.countDown()
            }
        }
        thread.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "server did not bind a socket")
        val response = Socket(InetAddress.getLoopbackAddress(), ready.port).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(gson.toJson(request))
                    gson.fromJson(reader.readLine(), PingResponse::class.java)
                }
            }
        }
        thread.join(5_000)
        assertTrue(!thread.isAlive, "server did not exit after one request")
        return response
    }

    private fun exchange(port: Int, request: Any): PingResponse =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(gson.toJson(request))
                    gson.fromJson(reader.readLine(), PingResponse::class.java)
                }
            }
        }

    private fun exchangeControl(port: Int, request: Any): DaemonControlResponse =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(gson.toJson(request))
                    gson.fromJson(reader.readLine(), DaemonControlResponse::class.java)
                }
            }
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

private class RecordingProjectSessionOpener : MpsProjectSessionOpener {
    var config: MpsProjectSessionConfig? = null

    override fun <T> withOpenProject(config: MpsProjectSessionConfig, action: () -> T): T {
        this.config = config
        return action()
    }
}
