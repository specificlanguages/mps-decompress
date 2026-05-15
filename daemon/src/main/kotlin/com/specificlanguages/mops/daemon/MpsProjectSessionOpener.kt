package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.project.Project

interface MpsProjectSessionOpener {
    fun <T> withOpenProject(config: MpsProjectSessionConfig, action: (Project) -> T): T
}
