package com.specificlanguages.mops.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories

object DaemonLog {
    fun append(logPath: Path, message: String) {
        logPath.parent.createDirectories()
        Files.writeString(
            logPath,
            "${Instant.now()} $message\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }
}
