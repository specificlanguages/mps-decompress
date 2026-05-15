package com.specificlanguages.mops.daemon

import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import org.jetbrains.mps.openapi.project.Project

/**
 * Opens an MPS project through the project-loader library used by the daemon.
 *
 * The opener is intentionally small so tests can replace it without booting MPS, while production still gets
 * ProjectLoader's environment initialization and disposal behavior.
 */
class ProjectLoaderMpsProjectSessionOpener : MpsProjectSessionOpener {
    override fun <T> withOpenProject(config: MpsProjectSessionConfig, action: (Project) -> T): T {
        val loader = ProjectLoader.build { environmentKind = EnvironmentKind.IDEA }
        return loader.executeWithProject(config.projectPath.toFile()) { _, project -> action(project) }
    }
}
