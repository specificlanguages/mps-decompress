package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class DaemonStatusStopCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `daemon status reads the current project daemon record without mps home`() {
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
}
