package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
        val model = project.resolve("solutions/foo/models/main.mps")
        model.parent.createDirectories()
        Files.writeString(model, "<model />")
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
        val project = tempDir.mpsProject()
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
        val project = tempDir.mpsProject()
        val nested = project.resolve("a/b/c").createDirectories()

        assertEquals(project, inferProjectPath(nested))
        assertNull(inferProjectPath(tempDir.resolve("outside").createDirectories()))
    }
}
