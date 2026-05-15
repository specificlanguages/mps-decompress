package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class ProcessDaemonLauncherTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `daemon ping reuses an existing project daemon record`() {
        val project = tempDir.mpsProject()
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

        val exitCode = newCommandLine(
            launcher = ProcessDaemonLauncher(
                environment = daemonEnvironment(daemonHome),
            ),
            environment = daemonEnvironment(daemonHome),
            workingDirectory = project,
        ).execute("--mps-home", "/opt/mps", "daemon", "ping")

        fakeDaemon.join()
        assertEquals(0, exitCode)
        assertContains(fakeDaemon.requestLine, "\"type\":\"ping\"")
        assertContains(fakeDaemon.requestLine, "\"token\":\"secret\"")
    }

    @Test
    fun `daemon ping removes stale daemon record before attempting autostart`() {
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")
        val selectedJava = daemonHome.resolve("selected-java.txt")
        val bundledJava = mpsHome.resolve("jbr/bin/java")
        bundledJava.parent.createDirectories()
        Files.writeString(
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
        val project = tempDir.mpsProject()
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
