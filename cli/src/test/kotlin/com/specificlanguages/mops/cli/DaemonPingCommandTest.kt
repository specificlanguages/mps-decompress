package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DaemonPingCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `daemon ping uses explicit mps home and inferred project`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val child = project.resolve("solutions/foo").createDirectories()
        val launcher = RecordingLauncher()
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = launcher,
            environment = emptyMap(),
            workingDirectory = child,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("--mps-home", "/opt/mps", "daemon", "ping")

        assertEquals(0, exitCode)
        assertEquals(project, launcher.projectPath)
        assertEquals(Path.of("/opt/mps").toAbsolutePath(), launcher.mpsHome)
        assertContains(stdout.toString(), "\"status\":\"ok\"")
        assertContains(stdout.toString(), "\"environmentReady\":true")
        assertContains(stdout.toString(), "\"projectPath\":\"${project.pathString}\"")
    }

    @Test
    fun `daemon ping uses MOPS_MPS_HOME fallback`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val launcher = RecordingLauncher()

        val exitCode = newCommandLine(
            launcher = launcher,
            environment = mapOf("MOPS_MPS_HOME" to "/env/mps"),
            workingDirectory = project,
        ).execute("daemon", "ping")

        assertEquals(0, exitCode)
        assertEquals(Path.of("/env/mps").toAbsolutePath(), launcher.mpsHome)
    }

    @Test
    fun `daemon ping reports missing mps home`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = emptyMap(),
            workingDirectory = project,
        ).also {
            it.err = PrintWriter(stderr, true)
        }.execute("daemon", "ping")

        assertEquals(2, exitCode)
        assertContains(stderr.toString(), "daemon ping requires MPS home")
        assertContains(stderr.toString(), "MOPS_MPS_HOME")
    }

    @Test
    fun `daemon ping reports missing project marker`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = mapOf("MOPS_MPS_HOME" to "/env/mps"),
            workingDirectory = tempDir,
        ).also {
            it.err = PrintWriter(stderr, true)
        }.execute("daemon", "ping")

        assertEquals(2, exitCode)
        assertContains(stderr.toString(), "no .mps directory found")
    }

    @Test
    fun `model resave routes target through daemon launcher`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val model = project.resolve("solutions/foo/models/main.mps")
        model.parent.createDirectories()
        java.nio.file.Files.writeString(model, "<model />")
        val launcher = RecordingLauncher()
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = launcher,
            environment = emptyMap(),
            workingDirectory = tempDir,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("--mps-home", "/opt/mps", "model", "resave", model.pathString)

        assertEquals(0, exitCode)
        assertEquals(project, launcher.projectPath)
        assertEquals(Path.of("/opt/mps").toAbsolutePath(), launcher.mpsHome)
        assertEquals(model.toAbsolutePath().normalize(), launcher.modelTarget)
        assertContains(stdout.toString(), "resaved")
        assertContains(stdout.toString(), model.pathString)
    }

    @Test
    fun `model resave requires mps home`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val model = project.resolve("models/main.mps")
        model.parent.createDirectories()
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = emptyMap(),
            workingDirectory = tempDir,
        ).also {
            it.err = PrintWriter(stderr, true)
        }.execute("model", "resave", model.pathString)

        assertEquals(2, exitCode)
        assertContains(stderr.toString(), "model resave requires MPS home")
        assertContains(stderr.toString(), "MOPS_MPS_HOME")
    }

    @Test
    fun `mps home resolver prefers explicit option`() {
        assertEquals("/cli", resolveMpsHome("/cli", mapOf("MOPS_MPS_HOME" to "/env")))
        assertEquals("/env", resolveMpsHome(null, mapOf("MOPS_MPS_HOME" to "/env")))
        assertNull(resolveMpsHome("", mapOf("MOPS_MPS_HOME" to "")))
    }

    @Test
    fun `project inference walks upward to mps directory`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val nested = project.resolve("a/b/c").createDirectories()

        assertEquals(project, inferProjectPath(nested))
        assertNull(inferProjectPath(tempDir.resolve("outside").createDirectories()))
    }

    @Test
    fun `daemon launch uses user-level state outside project and MPS jvm settings`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2024.1\n")
        mpsHome.resolve("lib/jna").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
        )

        assertTrue(launch.stateDir.pathString.startsWith(daemonHome.pathString))
        assertTrue(!launch.ideaConfigDir.pathString.startsWith(project.pathString))
        assertTrue(launch.ideaConfigDir.exists())
        assertTrue(launch.ideaSystemDir.exists())
        assertContains(launch.jvmArgs, "-Dmops.mps.home=${mpsHome.pathString}")
        assertContains(launch.jvmArgs, "-Didea.config.path=${launch.ideaConfigDir.pathString}")
        assertContains(launch.jvmArgs, "-Didea.system.path=${launch.ideaSystemDir.pathString}")
        assertContains(launch.jvmArgs, "-Djna.boot.library.path=${mpsHome.resolve("lib/jna").pathString}")
        assertTrue(launch.jvmArgs.any { it.startsWith("--add-opens=java.base/java.lang=") })
    }

    @Test
    fun `daemon launch still prepares a log path when mps home is invalid`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = mapOf("MOPS_DAEMON_HOME" to tempDir.resolve("daemon-home").pathString),
        )

        assertTrue(launch.logPath.parent.exists())
        assertTrue(launch.jvmArgs.any { it.startsWith("--add-opens=java.base/java.lang=") })
    }

    @Test
    fun `daemon status reads the current project daemon record without mps home`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val record = DaemonRecord(
            port = 4321,
            token = "secret",
            pid = 1234,
            protocolVersion = 1,
            daemonVersion = "0.3.0-SNAPSHOT",
            projectPath = project.pathString,
            mpsHome = "/opt/mps",
            logPath = daemonHome.resolve("projects/example/logs/daemon.log").pathString,
            startupTime = "2026-05-12T12:00:00Z",
        )
        DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString)).write(record)
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("daemon", "status")

        assertEquals(0, exitCode)
        assertContains(stdout.toString(), "running")
        assertContains(stdout.toString(), project.pathString)
        assertContains(stdout.toString(), "4321")
        assertContains(stdout.toString(), "/opt/mps")
    }

    @Test
    fun `daemon status all lists every daemon record without project inference`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString))
        store.write(
            DaemonRecord(
                port = 1111,
                token = "one",
                pid = 1,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = tempDir.resolve("one").pathString,
                mpsHome = "/mps/one",
                logPath = "/logs/one.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )
        store.write(
            DaemonRecord(
                port = 2222,
                token = "two",
                pid = 2,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = tempDir.resolve("two").pathString,
                mpsHome = "/mps/two",
                logPath = "/logs/two.log",
                startupTime = "2026-05-12T12:01:00Z",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            workingDirectory = tempDir,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("daemon", "status", "--all")

        assertEquals(0, exitCode)
        assertContains(stdout.toString(), tempDir.resolve("one").pathString)
        assertContains(stdout.toString(), tempDir.resolve("two").pathString)
        assertContains(stdout.toString(), "1111")
        assertContains(stdout.toString(), "2222")
    }

    @Test
    fun `daemon stop sends shutdown and removes the current project record`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString))
        val serverReady = CountDownLatch(1)
        lateinit var requestLine: String
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            server.use {
                serverReady.countDown()
                it.accept().use { socket ->
                    requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    PrintWriter(socket.getOutputStream(), true).println(
                        """{"type":"stop","status":"ok","protocolVersion":1}""",
                    )
                }
            }
        }
        serverThread.start()
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
        store.write(
            DaemonRecord(
                port = server.localPort,
                token = "secret",
                pid = 1234,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = project.pathString,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("daemon", "stop")

        serverThread.join(5_000)
        assertEquals(0, exitCode)
        assertContains(requestLine, "\"type\":\"stop\"")
        assertContains(requestLine, "\"token\":\"secret\"")
        assertContains(stdout.toString(), "stopped")
        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping reuses an existing project daemon record`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString))
        val serverReady = CountDownLatch(1)
        lateinit var requestLine: String
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            server.use {
                serverReady.countDown()
                it.accept().use { socket ->
                    requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    PrintWriter(socket.getOutputStream(), true).println(
                        """{"type":"ping","status":"ok","protocolVersion":1,"projectPath":"${project.pathString}","mpsHome":"/opt/mps","environmentReady":true,"logPath":"/logs/daemon.log"}""",
                    )
                }
            }
        }
        serverThread.start()
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
        store.write(
            DaemonRecord(
                port = server.localPort,
                token = "secret",
                pid = 1234,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = project.pathString,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = ProcessDaemonLauncher(
                environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            ),
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("--mps-home", "/opt/mps", "daemon", "ping")

        serverThread.join(5_000)
        assertEquals(0, exitCode)
        assertContains(requestLine, "\"type\":\"ping\"")
        assertContains(requestLine, "\"token\":\"secret\"")
        assertContains(stdout.toString(), "\"environmentReady\":true")
    }

    @Test
    fun `daemon ping removes stale daemon record before attempting autostart`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString))
        store.write(
            DaemonRecord(
                port = 9,
                token = "secret",
                pid = 1234,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = project.pathString,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )

        assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            ).ping(project, Path.of("/opt/mps"))
        }

        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping fails when project is owned by different mps home`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val serverReady = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            server.use {
                serverReady.countDown()
                it.accept().use { socket ->
                    BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    PrintWriter(socket.getOutputStream(), true).println(
                        """{"type":"ping","status":"ok","protocolVersion":1,"projectPath":"${project.pathString}","mpsHome":"/other/mps","environmentReady":true,"logPath":"/logs/daemon.log"}""",
                    )
                }
            }
        }
        serverThread.start()
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
        DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString)).write(
            DaemonRecord(
                port = server.localPort,
                token = "secret",
                pid = 1234,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = project.pathString,
                mpsHome = "/other/mps",
                logPath = "/logs/daemon.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            ).ping(project, Path.of("/opt/mps"))
        }

        serverThread.join(5_000)
        assertContains(exception.message ?: "", "different MPS home")
        assertContains(exception.message ?: "", "/other/mps")
    }

    @Test
    fun `daemon ping removes stale daemon record before rejecting a different mps home`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString))
        store.write(
            DaemonRecord(
                port = 9,
                token = "secret",
                pid = 1234,
                protocolVersion = 1,
                daemonVersion = "0.3.0-SNAPSHOT",
                projectPath = project.pathString,
                mpsHome = "/stale/mps",
                logPath = "/logs/daemon.log",
                startupTime = "2026-05-12T12:00:00Z",
            ),
        )

        assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
            ).ping(project, Path.of("/new/mps"))
        }

        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping kills newly started daemon when ready handshake is incompatible`() {
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")
        val classpath = System.getProperty("java.class.path")

        val exception = assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = mapOf(
                    "MOPS_DAEMON_HOME" to daemonHome.pathString,
                    "MOPS_DAEMON_CLASSPATH" to classpath,
                ),
            ).ping(project, mpsHome)
        }

        assertContains(exception.message ?: "", "compatible ready message")
        val pidFile = daemonHome.resolve("projects/${DaemonRecordStore.projectKey(project)}/logs/fake-daemon.pid")
        val pid = pidFile.readText().trim().toLong()
        assertProcessStops(pid)
    }

    private fun assertProcessStops(pid: Long) {
        repeat(50) {
            val handle = ProcessHandle.of(pid)
            if (handle.isEmpty || !handle.get().isAlive) {
                return
            }
            Thread.sleep(100)
        }
        ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
        throw AssertionError("daemon process $pid was still alive after failed startup")
    }
}

private class RecordingLauncher : DaemonProcessLauncher {
    var projectPath: Path? = null
    var mpsHome: Path? = null
    var modelTarget: Path? = null

    override fun ping(projectPath: Path, mpsHome: Path): PingResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        return PingResponse(
            type = "ping",
            status = "ok",
            protocolVersion = 1,
            projectPath = projectPath.pathString,
            mpsHome = mpsHome.pathString,
            environmentReady = true,
        )
    }

    override fun resave(projectPath: Path, mpsHome: Path, modelTarget: Path): ModelResaveResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        this.modelTarget = modelTarget
        return ModelResaveResponse(
            type = "model-resave",
            status = "ok",
            protocolVersion = 1,
            projectPath = projectPath.pathString,
            mpsHome = mpsHome.pathString,
            modelTarget = modelTarget.pathString,
        )
    }
}
