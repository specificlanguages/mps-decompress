package com.specificlanguages.mops.cli

import com.specificlanguages.mops.launcher.MpsDistributionLayout
import com.specificlanguages.mops.launcher.MpsLaunchArgs
import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Fully prepared filesystem and JVM launch context for one project daemon process.
 *
 * The state directory is keyed by normalized project path, so each MPS project gets an isolated daemon working
 * directory, IntelliJ config/system directories, log file, and daemon record.
 */
data class DaemonLaunch(
    val projectPath: Path,
    val mpsHome: Path,
    val stateDir: Path,
    val workDir: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
    val javaHome: Path,
    val jvmArgs: List<String>,
) {
    companion object {
        fun prepare(projectPath: Path, mpsHome: Path, javaHome: Path?, environment: Map<String, String>): DaemonLaunch {
            val normalizedProject = projectPath.toRealPath()
            val normalizedMpsHome = mpsHome.toRealPath()
            val projectState = DaemonRecordStore(environment).projectStateDir(normalizedProject)
            val workDir = projectState.resolve("daemon")
            val ideaConfigDir = workDir.resolve("idea-config")
            val ideaSystemDir = workDir.resolve("idea-system")
            val logDir = projectState.resolve("logs").createDirectories()
            workDir.createDirectories()
            ideaConfigDir.createDirectories()
            ideaSystemDir.createDirectories()
            val logPath = logDir.resolve("daemon.log")

            val launchJvmArgs = MpsLaunchArgs.getJvmArgsFor(mpsHome)
            launchJvmArgs += listOf("-Didea.config.path=$ideaConfigDir", "-Didea.system.path=$ideaSystemDir")

            val effectiveJavaHome = javaHome ?: MpsDistributionLayout.findBundledJavaHome(mpsHome) ?:
                throw IllegalStateException("MPS distribution at $mpsHome does not bundle Java, specify Java home explicitly")

            return DaemonLaunch(
                projectPath = normalizedProject,
                mpsHome = normalizedMpsHome,
                stateDir = projectState,
                workDir = workDir,
                ideaConfigDir = ideaConfigDir,
                ideaSystemDir = ideaSystemDir,
                logPath = logPath,
                javaHome = effectiveJavaHome,
                jvmArgs = launchJvmArgs,
            )
        }
    }
}
