package com.specificlanguages.mops.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DaemonLaunchTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `daemon java executable uses platform specific layout`() {
        val macHome = tempDir.resolve("mac-jbr").createDirectories()
        val macExecutable = macHome.resolve("Contents/Home/bin/java")
        macExecutable.parent.createDirectories()
        Files.writeString(macExecutable, "")
        val windowsHome = tempDir.resolve("windows-jbr").createDirectories()
        val linuxHome = tempDir.resolve("linux-jbr").createDirectories()

        assertEquals(macExecutable, DaemonJavaHome.executableIn(macHome, "Mac OS X"))
        assertEquals(windowsHome.resolve("bin/java.exe"), DaemonJavaHome.executableIn(windowsHome, "Windows 11"))
        assertEquals(linuxHome.resolve("bin/java"), DaemonJavaHome.executableIn(linuxHome, "Linux"))
    }

    @Test
    fun `daemon launch uses user-level state outside project and MPS jvm settings`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2024.1\n")
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
        val project = tempDir.mpsProject()
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
        Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=2025.10\n")
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
}
