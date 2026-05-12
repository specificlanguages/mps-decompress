package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

fun resolveMpsHome(cliValue: String?, environment: Map<String, String>): String? =
    cliValue?.takeIf { it.isNotBlank() }
        ?: environment["MOPS_MPS_HOME"]?.takeIf { it.isNotBlank() }

fun inferProjectPath(start: Path): Path? {
    var current: Path? = start.absolute().normalize()
    while (current != null) {
        if (current.resolve(".mps").exists() && current.resolve(".mps").isDirectory()) {
            return current
        }
        current = current.parent
    }
    return null
}
