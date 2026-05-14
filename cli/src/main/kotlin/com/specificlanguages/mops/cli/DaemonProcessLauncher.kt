package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.PingResponse
import java.nio.file.Path

interface DaemonProcessLauncher {
    fun ping(projectPath: Path, mpsHome: Path, javaHome: Path?): PingResponse
    fun resave(projectPath: Path, mpsHome: Path, javaHome: Path?, modelTarget: Path): DaemonResponse
}
