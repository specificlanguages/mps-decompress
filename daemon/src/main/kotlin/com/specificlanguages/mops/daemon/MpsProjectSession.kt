package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.project.Project

data class MpsProjectSession(
    val environment: MpsEnvironmentState,
    val project: Project,
)
