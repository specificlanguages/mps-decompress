package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.PingResponse
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
        val project = mpsProject()
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
        val project = mpsProject()
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
    fun `daemon ping passes explicit java home to daemon launcher`() {
        val project = mpsProject()
        val launcher = RecordingLauncher()

        val exitCode = newCommandLine(
            launcher = launcher,
            environment = emptyMap(),
            workingDirectory = project,
        ).execute("--java-home", "/opt/jbr", "--mps-home", "/opt/mps", "daemon", "ping")

        assertEquals(0, exitCode)
        assertEquals(Path.of("/opt/jbr").toAbsolutePath(), launcher.javaHome)
    }

    @Test
    fun `daemon ping reports missing mps home`() {
        val project = mpsProject()
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
        val project = mpsProject()
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
        }.execute("--java-home", "/opt/jbr", "--mps-home", "/opt/mps", "model", "resave", model.pathString)

        assertEquals(0, exitCode)
        assertEquals(project, launcher.projectPath)
        assertEquals(Path.of("/opt/mps").toAbsolutePath(), launcher.mpsHome)
        assertEquals(Path.of("/opt/jbr").toAbsolutePath(), launcher.javaHome)
        assertEquals(model.toAbsolutePath().normalize(), launcher.modelTarget)
        assertContains(stdout.toString(), "resaved")
        assertContains(stdout.toString(), model.pathString)
    }

    @Test
    fun `model resave requires mps home`() {
        val project = mpsProject()
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
    fun `daemon java executable uses platform specific layout`() {
        val macHome = tempDir.resolve("mac-jbr").createDirectories()
        val macExecutable = macHome.resolve("Contents/Home/bin/java")
        macExecutable.parent.createDirectories()
        java.nio.file.Files.writeString(macExecutable, "")
        val windowsHome = tempDir.resolve("windows-jbr").createDirectories()
        val linuxHome = tempDir.resolve("linux-jbr").createDirectories()

        assertEquals(macExecutable, DaemonJavaHome.executableIn(macHome, "Mac OS X"))
        assertEquals(windowsHome.resolve("bin/java.exe"), DaemonJavaHome.executableIn(windowsHome, "Windows 11"))
        assertEquals(linuxHome.resolve("bin/java"), DaemonJavaHome.executableIn(linuxHome, "Linux"))
    }

    @Test
    fun `project inference walks upward to mps directory`() {
        val project = mpsProject()
        val nested = project.resolve("a/b/c").createDirectories()

        assertEquals(project, inferProjectPath(nested))
        assertNull(inferProjectPath(tempDir.resolve("outside").createDirectories()))
    }

    @Test
    fun `daemon launch uses user-level state outside project and MPS jvm settings`() {
        val project = mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2024.1\n")
        mpsHome.resolve("lib/jna").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = daemonEnvironment(daemonHome),
        )

        assertTrue(launch.stateDir.pathString.startsWith(daemonHome.pathString))
        assertTrue(!launch.ideaConfigDir.pathString.startsWith(project.pathString))
        assertTrue(launch.ideaConfigDir.exists())
        assertTrue(launch.ideaSystemDir.exists())
        assertContains(launch.jvmArgs, "-Didea.config.path=${launch.ideaConfigDir.pathString}")
        assertContains(launch.jvmArgs, "-Didea.system.path=${launch.ideaSystemDir.pathString}")
        assertContains(launch.jvmArgs, "-Djna.boot.library.path=${mpsHome.resolve("lib/jna").pathString}")
        assertTrue(launch.jvmArgs.any { it.startsWith("--add-opens=java.base/java.lang=") })
    }

    @Test
    fun `daemon launch still prepares a log path when mps home is invalid`() {
        val project = mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = daemonEnvironment(tempDir.resolve("daemon-home")),
        )

        assertTrue(launch.logPath.parent.exists())
        assertTrue(launch.jvmArgs.any { it.startsWith("--add-opens=java.base/java.lang=") })
    }

    @Test
    fun `mps jvm args compare build versions numerically`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2025.10\n")
        mpsHome.resolve("lib/jna").createDirectories()

        val args = MpsJvmArgs.forMpsHome(
            mpsHome = mpsHome,
            ideaConfigDir = tempDir.resolve("config"),
            ideaSystemDir = tempDir.resolve("system"),
        )

        assertContains(args, "-Didea.platform.prefix=MPS")
        assertContains(args, "-Dintellij.platform.load.app.info.from.resources=true")
        assertContains(args, "-Djna.boot.library.path=${mpsHome.resolve("lib/jna").pathString}")
    }

    @Test
    fun `daemon status reads the current project daemon record without mps home`() {
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val record = daemonRecord(
            port = 4321,
            project = project,
            mpsHome = "/opt/mps",
            logPath = daemonHome.resolve("projects/example/logs/daemon.log").pathString,
        )
        DaemonRecordStore(daemonEnvironment(daemonHome)).write(record)
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = daemonEnvironment(daemonHome),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("daemon", "status")

        assertEquals(0, exitCode)
        val output = stdout.toString()
        assertContains(output, "running")
        assertContains(output, project.pathString)
        assertContains(output, "4321")
        assertContains(output, "/opt/mps")
    }

    @Test
    fun `daemon status all lists every daemon record without project inference`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(daemonEnvironment(daemonHome))
        store.write(
            daemonRecord(
                port = 1111,
                token = "one",
                pid = 1,
                project = tempDir.resolve("one"),
                mpsHome = "/mps/one",
                logPath = "/logs/one.log",
            ),
        )
        store.write(
            daemonRecord(
                port = 2222,
                token = "two",
                pid = 2,
                project = tempDir.resolve("two"),
                mpsHome = "/mps/two",
                logPath = "/logs/two.log",
                startupTime = "2026-05-12T12:01:00Z",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = daemonEnvironment(daemonHome),
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
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(daemonEnvironment(daemonHome))
        val fakeDaemon = startOneShotDaemon("""{"type":"stop","status":"ok","protocolVersion":1}""")
        store.write(
            daemonRecord(
                port = fakeDaemon.port,
                project = project,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = RecordingLauncher(),
            environment = daemonEnvironment(daemonHome),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("daemon", "stop")

        fakeDaemon.join()
        assertEquals(0, exitCode)
        assertContains(fakeDaemon.requestLine, "\"type\":\"stop\"")
        assertContains(fakeDaemon.requestLine, "\"token\":\"secret\"")
        assertContains(stdout.toString(), "stopped")
        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping reuses an existing project daemon record`() {
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(daemonEnvironment(daemonHome))
        val fakeDaemon = startOneShotDaemon(
            """{"type":"ping","status":"ok","protocolVersion":1,"projectPath":"${project.pathString}","mpsHome":"/opt/mps","environmentReady":true,"logPath":"/logs/daemon.log"}""",
        )
        store.write(
            daemonRecord(
                port = fakeDaemon.port,
                project = project,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            launcher = ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome),
            ),
            environment = daemonEnvironment(daemonHome),
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute("--mps-home", "/opt/mps", "daemon", "ping")

        fakeDaemon.join()
        assertEquals(0, exitCode)
        assertContains(fakeDaemon.requestLine, "\"type\":\"ping\"")
        assertContains(fakeDaemon.requestLine, "\"token\":\"secret\"")
        assertContains(stdout.toString(), "\"environmentReady\":true")
    }

    @Test
    fun `daemon ping removes stale daemon record before attempting autostart`() {
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(daemonEnvironment(daemonHome))
        store.write(
            daemonRecord(
                port = 9,
                project = project,
                mpsHome = "/opt/mps",
                logPath = "/logs/daemon.log",
            ),
        )

        assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome),
            ).ping(project, Path.of("/opt/mps"), null)
        }

        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping fails when project is owned by different mps home`() {
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val fakeDaemon = startOneShotDaemon(
            """{"type":"ping","status":"ok","protocolVersion":1,"projectPath":"${project.pathString}","mpsHome":"/other/mps","environmentReady":true,"logPath":"/logs/daemon.log"}""",
        )
        DaemonRecordStore(daemonEnvironment(daemonHome)).write(
            daemonRecord(
                port = fakeDaemon.port,
                project = project,
                mpsHome = "/other/mps",
                logPath = "/logs/daemon.log",
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome),
            ).ping(project, Path.of("/opt/mps"), null)
        }

        fakeDaemon.join()
        assertContains(exception.message ?: "", "different MPS home")
        assertContains(exception.message ?: "", "/other/mps")
    }

    @Test
    fun `daemon ping removes stale daemon record before rejecting a different mps home`() {
        val project = mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore(daemonEnvironment(daemonHome))
        store.write(
            daemonRecord(
                port = 9,
                project = project,
                mpsHome = "/stale/mps",
                logPath = "/logs/daemon.log",
            ),
        )

        assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome),
            ).ping(project, Path.of("/new/mps"), null)
        }

        assertNull(store.read(project))
    }

    @Test
    fun `daemon ping kills newly started daemon when ready handshake is incompatible`() {
        val project = mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")
        val classpath = System.getProperty("java.class.path")

        val exception = assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome, "MOPS_DAEMON_CLASSPATH" to classpath),
            ).ping(project, mpsHome, null)
        }

        assertContains(exception.message ?: "", "compatible ready message")
        val pidFile = daemonHome.resolve("projects/${DaemonRecordStore.projectKey(project)}/logs/fake-daemon.pid")
        val pid = pidFile.readText().trim().toLong()
        assertProcessStops(pid)
    }

    @Test
    fun `daemon ping starts daemon with bundled MPS jbr when java home is not explicit`() {
        val project = mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")
        val selectedJava = daemonHome.resolve("selected-java.txt")
        val bundledJava = mpsHome.resolve("jbr/bin/java")
        bundledJava.parent.createDirectories()
        java.nio.file.Files.writeString(
            bundledJava,
            """
            #!/bin/sh
            echo "$0" > "${selectedJava.pathString}"
            exec "${currentJavaExecutable().pathString}" "$@"
            """.trimIndent(),
        )
        bundledJava.toFile().setExecutable(true)

        assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(
                    daemonHome,
                    "MOPS_DAEMON_CLASSPATH" to System.getProperty("java.class.path"),
                ),
            ).ping(project, mpsHome, null)
        }

        assertEquals(bundledJava.pathString, selectedJava.readText().trim())
    }

    @Test
    fun `daemon ping reports startup error emitted before ready`() {
        val project = mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")

        val exception = assertFailsWith<IllegalStateException> {
            ProcessDaemonLauncher(
                environment = daemonEnvironment(
                    daemonHome,
                    "MOPS_DAEMON_CLASSPATH" to System.getProperty("java.class.path"),
                    "MOPS_FAKE_DAEMON_STARTUP_ERROR" to "1",
                ),
            ).ping(project, mpsHome, null)
        }

        assertContains(exception.message ?: "", "JVM_VERSION_MISMATCH")
        assertContains(exception.message ?: "", "required Java 21")
        assertContains(exception.message ?: "", "Daemon log:")
    }

    private fun mpsProject(name: String = "project"): Path {
        val project = tempDir.resolve(name).createDirectories()
        project.resolve(".mps").createDirectory()
        return project
    }

    private fun daemonRecord(
        project: Path,
        port: Int,
        token: String = "secret",
        pid: Long = 1234,
        mpsHome: String,
        logPath: String,
        startupTime: String = "2026-05-12T12:00:00Z",
    ): DaemonRecord =
        DaemonRecord(
            port = port,
            token = token,
            pid = pid,
            protocolVersion = 1,
            daemonVersion = "0.3.0-SNAPSHOT",
            projectPath = project.pathString,
            mpsHome = mpsHome,
            logPath = logPath,
            startupTime = startupTime,
        )

    private fun daemonEnvironment(daemonHome: Path, vararg entries: Pair<String, String>): Map<String, String> =
        buildMap {
            put("MOPS_DAEMON_HOME", daemonHome.pathString)
            putAll(entries)
        }

    private fun startOneShotDaemon(response: String): OneShotDaemon {
        val serverReady = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val daemon = OneShotDaemon(server.localPort)
        daemon.thread = Thread {
            server.use {
                serverReady.countDown()
                it.accept().use { socket ->
                    daemon.requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    PrintWriter(socket.getOutputStream(), true).println(response)
                }
            }
        }
        daemon.thread.start()
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
        return daemon
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

    private fun currentJavaExecutable(): Path =
        Path.of(System.getProperty("java.home"))
            .resolve("bin")
            .resolve(if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
}

private class OneShotDaemon(
    val port: Int,
) {
    lateinit var thread: Thread
    lateinit var requestLine: String

    fun join() {
        thread.join(5_000)
    }
}

private class RecordingLauncher : DaemonProcessLauncher {
    var projectPath: Path? = null
    var mpsHome: Path? = null
    var javaHome: Path? = null
    var modelTarget: Path? = null

    override fun ping(projectPath: Path, mpsHome: Path, javaHome: Path?): PingResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        this.javaHome = javaHome
        return PingResponse(
            type = "ping",
            status = "ok",
            protocolVersion = 1,
            projectPath = projectPath.pathString,
            mpsHome = mpsHome.pathString,
            environmentReady = true,
        )
    }

    override fun resave(projectPath: Path, mpsHome: Path, javaHome: Path?, modelTarget: Path): ModelResaveResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        this.javaHome = javaHome
        this.modelTarget = modelTarget
        return ModelResaveResponse(
            type = "model-resave",
            status = "ok",
            protocolVersion = 1,
            modelTarget = modelTarget.pathString,
        )
    }
}
