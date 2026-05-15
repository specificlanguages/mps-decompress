package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.absolute
import kotlin.io.path.pathString

class ProcessDaemonLauncher(
    private val environment: Map<String, String> = System.getenv(),
    private val timeout: Duration = Duration.ofSeconds(15),
) : DaemonProcessLauncher {
    override fun ping(projectPath: Path, mpsHome: Path, javaHome: Path?): PingResponse {
        return ensureDaemon(projectPath, mpsHome, javaHome).ping
    }

    override fun resave(projectPath: Path, mpsHome: Path, javaHome: Path?, modelTarget: Path): DaemonResponse {
        val record = ensureDaemon(projectPath, mpsHome, javaHome).record
        return DaemonClient(timeout).resave(record, modelTarget.absolute().normalize())
    }

    private fun ensureDaemon(projectPath: Path, mpsHome: Path, javaHome: Path?): DaemonReady {
        val records = DaemonRecordStore(environment)
        val normalizedProject = projectPath.absolute().normalize()
        val normalizedMpsHome = mpsHome.absolute().normalize()
        val existing = records.read(normalizedProject)
        if (existing != null) {
            if (existing.protocolVersion == ProtocolVersion) {
                val existingResponse = try {
                    DaemonClient(timeout).ping(existing)
                } catch (_: Exception) {
                    records.delete(normalizedProject)
                    null
                }
                if (existingResponse != null) {
                    if (Path.of(existing.mpsHome).absolute().normalize() != normalizedMpsHome) {
                        throw IllegalStateException(
                            "project is already owned by a mops daemon with a different MPS home: ${existing.mpsHome}",
                        )
                    }
                    return DaemonReady(existing, existingResponse)
                }
            } else {
                records.delete(normalizedProject)
            }
        }

        val token = UUID.randomUUID().toString()
        val daemonClasspath = DaemonClasspath(environment)
        val launch = DaemonLaunch.prepare(normalizedProject, normalizedMpsHome, environment)
        val runtimeClasspath = listOf(
            daemonClasspath.daemonClasspath(),
            daemonClasspath.mpsRuntimeClasspath(normalizedMpsHome),
        )
            .filter { it.isNotBlank() }
            .joinToString(java.io.File.pathSeparator)
        val processBuilder = ProcessBuilder(
            listOf(DaemonJavaHome.executable(javaHome, normalizedMpsHome).pathString) +
                launch.jvmArgs +
                listOf(
                    "-cp",
                    runtimeClasspath,
                    "com.specificlanguages.mops.daemon.MainKt",
                    "--project-path",
                    launch.projectPath.pathString,
                    "--mps-home",
                    launch.mpsHome.pathString,
                    "--token",
                    token,
                    "--idea-config-dir",
                    launch.ideaConfigDir.pathString,
                    "--idea-system-dir",
                    launch.ideaSystemDir.pathString,
                    "--log-path",
                    launch.logPath.pathString,
                ),
        )
            .directory(launch.workDir.toFile())
            .redirectError(ProcessBuilder.Redirect.appendTo(launch.logPath.toFile()))
        processBuilder.environment().putAll(environment)
        processBuilder.environment()["MOPS_MPS_HOME"] = normalizedMpsHome.pathString
        val process = processBuilder.start()

        var startupSucceeded = false
        try {
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val ready = DaemonStartupReader(timeout).readReadyMessage(stdout, process, launch.logPath)
            if (ready.protocolVersion != ProtocolVersion) {
                throw IllegalStateException("daemon did not report a compatible ready message")
            }

            val response = DaemonClient(timeout).ping(ready.port, token)

            if (response.status != "ok") {
                throw IllegalStateException("daemon ping failed with status ${response.status}")
            }
            startupSucceeded = true
            val record = records.read(normalizedProject)
                ?: throw IllegalStateException("daemon did not write its project record")
            return DaemonReady(record, response)
        } finally {
            if (!startupSucceeded && process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private data class DaemonReady(
        val record: DaemonRecord,
        val ping: PingResponse,
    )

}
