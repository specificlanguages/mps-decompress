package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.project.Project

/**
 * Testable boundary for opening an MPS project and exposing the loaded [Project] to daemon code.
 */
interface MpsProjectSessionOpener {
    fun <T> withOpenProject(config: MpsProjectSessionConfig, action: (Project) -> T): T
}
