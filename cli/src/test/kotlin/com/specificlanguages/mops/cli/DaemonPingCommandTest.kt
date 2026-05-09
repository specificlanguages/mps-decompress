package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

        val launch = SingleUseDaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = mapOf("MOPS_DAEMON_HOME" to daemonHome.pathString),
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
        val project = tempDir.resolve("project").createDirectories()
        project.resolve(".mps").createDirectory()
        val mpsHome = tempDir.resolve("mps").createDirectories()

        val launch = SingleUseDaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            environment = mapOf("MOPS_DAEMON_HOME" to tempDir.resolve("daemon-home").pathString),
        )

        assertTrue(launch.logPath.parent.exists())
        assertTrue(launch.jvmArgs.any { it.startsWith("--add-opens=java.base/java.lang=") })
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
            environmentReady = true,
        )
    }
}
