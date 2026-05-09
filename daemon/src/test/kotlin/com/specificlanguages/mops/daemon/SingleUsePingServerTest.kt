package com.specificlanguages.mops.daemon

import com.google.gson.Gson
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

class SingleUsePingServerTest {
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
        assertTrue(java.nio.file.Files.readString(environment.logPath).contains("environment ready"))
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
}
