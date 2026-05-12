package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.PingResponse
import java.nio.file.Path

interface DaemonProcessLauncher {
    fun ping(projectPath: Path, mpsHome: Path): PingResponse
    fun resave(projectPath: Path, mpsHome: Path, modelTarget: Path): DaemonResponse
}
