package com.specificlanguages.mops.daemon

import java.nio.file.Path

internal fun resolveMpsHomeFromRuntime(): Path =
    System.getProperty(MpsHomeProperty)
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }
        ?: System.getenv("MOPS_MPS_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
        ?: throw IllegalStateException(
            "MPS home is not configured for the daemon JVM; expected -D$MpsHomeProperty=<path>",
        )
