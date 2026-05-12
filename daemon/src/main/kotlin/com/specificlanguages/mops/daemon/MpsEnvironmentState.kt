package com.specificlanguages.mops.daemon

import java.nio.file.Path

data class MpsEnvironmentState(
    val projectPath: Path,
    val mpsHome: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
)
