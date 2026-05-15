package com.specificlanguages.mops.daemon

import java.nio.file.Path

/**
 * Inputs needed to open a project-loader-backed MPS project session.
 */
data class MpsProjectSessionConfig(
    val projectPath: Path,
    val mpsHome: Path,
    val pluginRoot: Path,
    val plugins: List<DetectedPlugin>,
    val buildNumber: String?,
)
