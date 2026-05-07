package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
}

private class RecordingLauncher : DaemonProcessLauncher {
    var projectPath: Path? = null
    var mpsHome: Path? = null

    override fun ping(projectPath: Path, mpsHome: Path): PingResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        return PingResponse(
            type = "ping",
            status = "ok",
            protocolVersion = 1,
            projectPath = projectPath.pathString,
            mpsHome = mpsHome.pathString,
        )
    }
}
