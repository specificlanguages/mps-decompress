package com.specificlanguages.mops.daemon

import java.nio.file.Path

data class DetectedPlugin(
    val id: String,
    val path: Path,
)
