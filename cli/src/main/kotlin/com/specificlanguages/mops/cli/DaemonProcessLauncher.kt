package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.PingResponse
import java.nio.file.Path

/**
 * Boundary between CLI commands and daemon process management.
 *
 * Tests can replace this interface to verify command parsing without launching MPS, while production uses
 * [ProcessDaemonLauncher] to start or reuse a per-project daemon.
 */
interface DaemonProcessLauncher {
    fun ping(projectPath: Path, mpsHome: Path, javaHome: Path?): PingResponse
    fun resave(projectPath: Path, mpsHome: Path, javaHome: Path?, modelTarget: Path): DaemonResponse
}
