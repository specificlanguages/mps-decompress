package com.specificlanguages.mops.daemon

import java.nio.file.Path

/**
 * Resolved project, MPS, IntelliJ state, and log locations for one daemon runtime.
 */
data class MpsEnvironmentState(
    val projectPath: Path,
    val mpsHome: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
)
