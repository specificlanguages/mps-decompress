package com.specificlanguages.mops.daemon

import java.nio.file.Path

/**
 * Plugin descriptor found while scanning an MPS installation.
 */
data class DetectedPlugin(
    val id: String,
    val path: Path,
)
