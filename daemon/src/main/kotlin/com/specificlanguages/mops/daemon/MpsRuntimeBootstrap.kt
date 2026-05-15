package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsBuildProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

class MpsRuntimeBootstrap(
    private val projectPath: Path,
    private val mpsHome: Path,
    private val ideaConfigDir: Path,
    private val ideaSystemDir: Path,
    private val logPath: Path,
    private val projectSessionOpener: MpsProjectSessionOpener = ProjectLoaderMpsProjectSessionOpener(),
) {
    fun initialize(): MpsEnvironmentState {
        logPath.parent.createDirectories()
        log("initializing MPS daemon runtime")
        requireDirectory(projectPath, "project path")
        requireDirectory(projectPath.resolve(".mps"), "MPS project marker")
        requireDirectory(mpsHome, "MPS home")
        requireFile(mpsHome.resolve("build.properties"), "MPS build properties")
        requireDirectory(mpsHome.resolve("plugins"), "MPS plugins directory")
        ideaConfigDir.createDirectories()
        ideaSystemDir.createDirectories()
        log("idea.config.path=${ideaConfigDir.pathString}")
        log("idea.system.path=${ideaSystemDir.pathString}")

        return MpsEnvironmentState(
            projectPath = projectPath,
            mpsHome = mpsHome,
            ideaConfigDir = ideaConfigDir,
            ideaSystemDir = ideaSystemDir,
            logPath = logPath,
        )
    }

    fun <T> withLoadedProject(action: (MpsEnvironmentState) -> T): T {
        val environment = initialize()
        val pluginRoot = mpsHome.resolve("plugins")
        val plugins = PluginScanner.findPlugins(pluginRoot)
        log("opening IDEA environment for project ${projectPath.pathString} with ${plugins.size} plugins from ${pluginRoot.pathString}")
        return projectSessionOpener.withOpenProject(
            MpsProjectSessionConfig(
                projectPath = projectPath,
                mpsHome = mpsHome,
                pluginRoot = pluginRoot,
                plugins = plugins,
                buildNumber = MpsBuildProperties.buildNumber(mpsHome),
            ),
        ) {
            log("environment ready for project ${projectPath.pathString}")
            action(environment)
        }
    }

    fun log(message: String) {
        logPath.parent.createDirectories()
        Files.writeString(
            logPath,
            "${Instant.now()} $message\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private fun requireDirectory(path: Path, label: String) {
        if (!Files.isDirectory(path)) {
            throw IllegalStateException("$label is not a directory: ${path.pathString}")
        }
    }

    private fun requireFile(path: Path, label: String) {
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("$label is missing: ${path.pathString}")
        }
    }

}
