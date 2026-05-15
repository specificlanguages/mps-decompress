package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories

data class DaemonLaunch(
    val projectPath: Path,
    val mpsHome: Path,
    val stateDir: Path,
    val workDir: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
    val jvmArgs: List<String>,
) {
    companion object {
        fun prepare(projectPath: Path, mpsHome: Path, environment: Map<String, String>): DaemonLaunch {
            val normalizedProject = projectPath.absolute().normalize()
            val normalizedMpsHome = mpsHome.absolute().normalize()
            val projectState = DaemonRecordStore(environment).projectStateDir(normalizedProject)
            val workDir = projectState.resolve("daemon")
            val ideaConfigDir = workDir.resolve("idea-config")
            val ideaSystemDir = workDir.resolve("idea-system")
            val logDir = projectState.resolve("logs").createDirectories()
            workDir.createDirectories()
            ideaConfigDir.createDirectories()
            ideaSystemDir.createDirectories()
            val logPath = logDir.resolve("daemon.log")

            return DaemonLaunch(
                projectPath = normalizedProject,
                mpsHome = normalizedMpsHome,
                stateDir = projectState,
                workDir = workDir,
                ideaConfigDir = ideaConfigDir,
                ideaSystemDir = ideaSystemDir,
                logPath = logPath,
                jvmArgs = MpsJvmArgs.forMpsHome(normalizedMpsHome, ideaConfigDir, ideaSystemDir),
            )
        }
    }
}
