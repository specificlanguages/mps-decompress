package com.specificlanguages.mops.daemon

import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.Plugin
import de.itemis.mps.gradle.project.loader.ProjectLoader
import kotlin.io.path.pathString

class ProjectLoaderMpsProjectSessionOpener : MpsProjectSessionOpener {
    override fun <T> withOpenProject(config: MpsProjectSessionConfig, action: () -> T): T {
        val loader = ProjectLoader.build {
            environmentKind = EnvironmentKind.IDEA
            buildNumber = config.buildNumber
            environmentConfig {
                pluginLocation = config.pluginRoot.toFile()
                plugins.addAll(config.plugins.map { Plugin(it.id, it.path.pathString) })
            }
        }
        return loader.executeWithProject(config.projectPath.toFile()) { _, _ -> action() }
    }
}
