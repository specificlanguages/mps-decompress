package com.specificlanguages.mops.cli

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

data class DaemonLaunch(
    val projectPath: Path,
    val mpsHome: Path,
    val stateDir: Path,
    val workDir: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
    val recordPath: Path,
    val jvmArgs: List<String>,
) {
    companion object {
        fun prepare(projectPath: Path, mpsHome: Path, environment: Map<String, String>): DaemonLaunch {
            val normalizedProject = projectPath.absolute().normalize()
            val normalizedMpsHome = mpsHome.absolute().normalize()
            val projectState = daemonBaseDir(environment)
                .resolve("projects")
                .resolve(sha256(normalizedProject.pathString))
            val workDir = projectState.resolve("daemon")
            val ideaConfigDir = workDir.resolve("idea-config")
            val ideaSystemDir = workDir.resolve("idea-system")
            val logDir = projectState.resolve("logs").createDirectories()
            workDir.createDirectories()
            ideaConfigDir.createDirectories()
            ideaSystemDir.createDirectories()
            val logPath = logDir.resolve("daemon-ping.log")
            val recordPath = DaemonRecordStore(environment).recordPath(normalizedProject)

            return DaemonLaunch(
                projectPath = normalizedProject,
                mpsHome = normalizedMpsHome,
                stateDir = projectState,
                workDir = workDir,
                ideaConfigDir = ideaConfigDir,
                ideaSystemDir = ideaSystemDir,
                logPath = logPath,
                recordPath = recordPath,
                jvmArgs = MpsJvmArgs.forMpsHome(normalizedMpsHome, ideaConfigDir, ideaSystemDir),
            )
        }

        private fun daemonBaseDir(environment: Map<String, String>): Path =
            environment["MOPS_DAEMON_HOME"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it).absolute().normalize() }
                ?: Path.of(System.getProperty("user.home"), ".mops", "daemon").absolute().normalize()

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}
