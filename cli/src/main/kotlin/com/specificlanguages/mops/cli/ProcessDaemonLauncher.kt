package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import com.specificlanguages.mops.protocol.ReadyMessage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.absolute
import kotlin.io.path.pathString

class ProcessDaemonLauncher(
    private val environment: Map<String, String> = System.getenv(),
    private val timeout: Duration = Duration.ofSeconds(15),
) : DaemonProcessLauncher {
    override fun ping(projectPath: Path, mpsHome: Path): PingResponse {
        return ensureDaemon(projectPath, mpsHome).ping
    }

    override fun resave(projectPath: Path, mpsHome: Path, modelTarget: Path): DaemonResponse {
        val record = ensureDaemon(projectPath, mpsHome).record
        return DaemonClient(timeout).resave(record, modelTarget.absolute().normalize())
    }

    private fun ensureDaemon(projectPath: Path, mpsHome: Path): DaemonReady {
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
        val daemonClasspath = resolveDaemonClasspath()
        val launch = DaemonLaunch.prepare(normalizedProject, normalizedMpsHome, environment)
        val runtimeClasspath = listOf(daemonClasspath, mpsRuntimeClasspath(normalizedMpsHome))
            .filter { it.isNotBlank() }
            .joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            listOf(javaExecutable().pathString) +
                launch.jvmArgs +
                listOf(
                    "-cp",
                    runtimeClasspath,
                    "com.specificlanguages.mops.daemon.MainKt",
                    "serve",
                    "--project-path",
                    launch.projectPath.pathString,
                    "--token",
                    token,
                    "--idea-config-dir",
                    launch.ideaConfigDir.pathString,
                    "--idea-system-dir",
                    launch.ideaSystemDir.pathString,
                    "--log-path",
                    launch.logPath.pathString,
                    "--record-path",
                    launch.recordPath.pathString,
                ),
        )
            .directory(launch.workDir.toFile())
            .redirectError(ProcessBuilder.Redirect.appendTo(launch.logPath.toFile()))
            .start()

        var startupSucceeded = false
        try {
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val readyLine = readLineWithProcessCheck(stdout, process, launch.logPath)
            val ready = GsonCodec.fromJson(readyLine, ReadyMessage::class.java)
            if (ready.type != "ready" || ready.protocolVersion != ProtocolVersion) {
                throw IllegalStateException("daemon did not report a compatible ready message")
            }

            val response = Socket(InetAddress.getLoopbackAddress(), ready.port).use { socket ->
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                        writer.println(
                            GsonCodec.toJson(
                                PingRequest(
                                    type = "ping",
                                    protocolVersion = ProtocolVersion,
                                    token = token,
                                ),
                            ),
                        )
                        GsonCodec.fromJson(reader.readLine(), PingResponse::class.java)
                    }
                }
            }

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
            if (!startupSucceeded && !process.isAlive && process.exitValue() != 0) {
                throw daemonStartupException("daemon exited with status ${process.exitValue()}", launch.logPath)
            }
        }
    }

    private fun resolveDaemonClasspath(): String =
        environment["MOPS_DAEMON_CLASSPATH"]?.takeIf { it.isNotBlank() }
            ?: System.getProperty("mops.daemon.classpath")?.takeIf { it.isNotBlank() }
            ?: discoverLocalDaemonClasspath()
            ?: throw IllegalStateException(
                "cannot start daemon: set MOPS_DAEMON_CLASSPATH to the daemon runtime classpath",
            )

    private fun mpsRuntimeClasspath(mpsHome: Path): String =
        buildList {
            addAll(jarsIn(mpsHome.resolve("lib")))
            addAll(jarsIn(mpsHome.resolve("lib/modules")))
        }.joinToString(File.pathSeparator)

    private fun jarsIn(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }
        return Files.list(directory).use { entries ->
            entries
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
                .sorted()
                .map { it.pathString }
                .toList()
        }
    }

    private fun discoverLocalDaemonClasspath(): String? =
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .asSequence()
            .mapNotNull { Path.of(it).toAbsolutePath().parent }
            .flatMap { parentsOf(it) }
            .flatMap {
                sequenceOf(
                    it.resolve("daemon/build/install/mops-daemon/lib"),
                    it.resolve("daemon/build/install/daemon/lib"),
                )
            }
            .firstOrNull { Files.isDirectory(it) }
            ?.let { libDir ->
                Files.list(libDir).use { entries ->
                    entries
                        .filter { it.fileName.toString().endsWith(".jar") }
                        .sorted()
                        .map { it.pathString }
                        .toList()
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(File.pathSeparator)
                }
            }

    private fun parentsOf(start: Path): Sequence<Path> = sequence {
        var current: Path? = start
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }

    private fun javaExecutable(): Path =
        Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")

    private fun readLineWithProcessCheck(reader: BufferedReader, process: Process, logPath: Path): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                return reader.readLine()
            }
            if (!process.isAlive) {
                throw daemonStartupException("daemon exited before reporting its socket port", logPath)
            }
            Thread.sleep(25)
        }
        throw daemonStartupException("timed out waiting for daemon socket port", logPath)
    }

    private fun daemonStartupException(message: String, logPath: Path): IllegalStateException =
        IllegalStateException("$message. Daemon log: ${logPath.pathString}")

    private data class DaemonReady(
        val record: DaemonRecord,
        val ping: PingResponse,
    )

}
