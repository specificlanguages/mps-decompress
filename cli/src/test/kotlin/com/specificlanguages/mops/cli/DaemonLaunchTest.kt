package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DaemonLaunchTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `daemon launch uses user-level state outside project and MPS jvm settings`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=MPS-242.1234.567\n")

        mpsHome.resolve("lib/jna").createDirectories()
        mpsHome.resolve("jbr/Contents/Home").createDirectories()

        val daemonHome = tempDir.resolve("daemon-home")

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            javaHome = null,
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
    fun `daemon launch uses bundled JBR if explicit not passed`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=MPS-242.1234.567\n")
        mpsHome.resolve("lib/jna").createDirectories()
        val daemonHome = tempDir.resolve("daemon-home")

        mpsHome.resolve("jbr/Contents/Home").createDirectories()

        val launch = DaemonLaunch.prepare(
            projectPath = project,
            mpsHome = mpsHome,
            javaHome = null,
            environment = daemonEnvironment(daemonHome),
        )

        assertTrue(launch.javaHome.pathString.startsWith(mpsHome.resolve("jbr").pathString))
    }
}
