package com.specificlanguages.mops.daemon

import jetbrains.mps.util.PathManager
import java.nio.file.Path

internal fun resolveMpsHomeFromRuntime(): Path {
    val pathString = System.getenv("MOPS_MPS_HOME")
        ?: PathManager.getHomePath()
        ?: throw IllegalStateException("Could not detect MPS home for the daemon JVM from the daemon's classpath. Set MOPS_MPS_HOME environment variable.")

    return Path.of(pathString)
}
