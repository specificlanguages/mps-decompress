package com.specificlanguages.mops.cli

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

private const val ProtocolVersion = 1
private val GsonCodec = Gson()

fun main(args: Array<String>) {
    exitProcess(newCommandLine().execute(*args))
}

fun newCommandLine(
    launcher: DaemonProcessLauncher = ProcessDaemonLauncher(),
    environment: Map<String, String> = System.getenv(),
    workingDirectory: Path = Path.of("").absolute(),
): CommandLine =
    CommandLine(MopsCommand(launcher, environment, workingDirectory))
        .setExecutionExceptionHandler { exception, commandLine, _ ->
            commandLine.err.println(exception.message ?: exception::class.java.name)
            1
        }

@Command(
    name = "mops",
    mixinStandardHelpOptions = true,
    version = ["mops 0.3.0-SNAPSHOT"],
    description = ["Kotlin CLI for the daemon-backed MPS prototype."],
    subcommands = [
        DaemonCommand::class,
        ModelCommand::class,
    ],
)
class MopsCommand(
    val launcher: DaemonProcessLauncher = ProcessDaemonLauncher(),
    val environment: Map<String, String> = System.getenv(),
    val workingDirectory: Path = Path.of("").absolute(),
) : Runnable {
    @Option(
        names = ["--mps-home"],
        paramLabel = "PATH",
        description = ["MPS home used by daemon-backed commands."],
    )
    var mpsHome: String? = null

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(
    name = "daemon",
    description = ["Inspect or control mops daemon processes."],
    subcommands = [
        DaemonPingCommand::class,
        DaemonStatusCommand::class,
        DaemonStopCommand::class,
    ],
)
class DaemonCommand : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "ping", description = ["Start or reuse a project daemon and exchange a ping request."])
class DaemonPingCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        val root = daemon.root
        val mpsHome = resolveMpsHome(root.mpsHome, root.environment)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "daemon ping requires MPS home; pass --mps-home <path> or set MOPS_MPS_HOME",
            )
        val projectPath = inferProjectPath(root.workingDirectory)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "cannot infer MPS project: no .mps directory found from ${root.workingDirectory.absolute()} upward",
            )

        val response = root.launcher.ping(projectPath, Path.of(mpsHome).absolute())
        spec.commandLine().out.println(GsonCodec.toJson(response))
    }
}

@Command(name = "status", description = ["Print daemon status."])
class DaemonStatusCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
    var all: Boolean = false

    override fun run() {
        val root = daemon.root
        val records = DaemonRecordStore(root.environment)
        val selected = if (all) {
            records.readAll()
        } else {
            val projectPath = inferProjectPath(root.workingDirectory)
                ?: throw CommandLine.ParameterException(
                    spec.commandLine(),
                    "cannot infer MPS project: no .mps directory found from ${root.workingDirectory.absolute()} upward",
                )
            listOfNotNull(records.read(projectPath))
        }

        if (selected.isEmpty()) {
            spec.commandLine().out.println("no mops daemons")
            return
        }

        selected.forEach { record ->
            spec.commandLine().out.println(
                "running project=${record.projectPath} port=${record.port} pid=${record.pid} mpsHome=${record.mpsHome} log=${record.logPath}",
            )
        }
    }
}

@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    override fun run() {
        val root = daemon.root
        val records = DaemonRecordStore(root.environment)
        val selected = if (all) {
            records.readAll()
        } else {
            val projectPath = inferProjectPath(root.workingDirectory)
                ?: throw CommandLine.ParameterException(
                    spec.commandLine(),
                    "cannot infer MPS project: no .mps directory found from ${root.workingDirectory.absolute()} upward",
                )
            listOfNotNull(records.read(projectPath))
        }

        if (selected.isEmpty()) {
            spec.commandLine().out.println("no mops daemons")
            return
        }

        selected.forEach { record ->
            try {
                DaemonClient().stop(record)
                records.delete(Path.of(record.projectPath))
                spec.commandLine().out.println("stopped project=${record.projectPath} pid=${record.pid}")
            } catch (_: Exception) {
                records.delete(Path.of(record.projectPath))
                spec.commandLine().out.println("removed stale daemon record for project=${record.projectPath}")
            }
        }
    }
}

@Command(
    name = "model",
    description = ["Run model operations through the mops daemon."],
    subcommands = [ModelResaveCommand::class],
)
class ModelCommand : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "resave", description = ["Resave one model through the mops daemon."])
class ModelResaveCommand : Runnable {
    @ParentCommand
    lateinit var model: ModelCommand

    @Spec
    lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "MODEL_TARGET", description = ["Persisted model path to resave."])
    lateinit var modelTarget: String

    override fun run() {
        val root = model.root
        val mpsHome = resolveMpsHome(root.mpsHome, root.environment)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "model resave requires MPS home; pass --mps-home <path> or set MOPS_MPS_HOME",
            )
        val resolvedTarget = Path.of(modelTarget).absolute().normalize()
        val projectPath = inferProjectPath(if (Files.isDirectory(resolvedTarget)) resolvedTarget else resolvedTarget.parent)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "cannot infer MPS project from model target: no .mps directory found from $resolvedTarget upward",
            )

        val response = root.launcher.resave(projectPath, Path.of(mpsHome).absolute(), resolvedTarget)
        if (response.status == "ok") {
            spec.commandLine().out.println("resaved ${response.modelTarget}")
        } else {
            val logSuffix = response.logPath?.let { " Daemon log: $it" } ?: ""
            throw IllegalStateException("${response.message ?: "model resave failed"}$logSuffix")
        }
    }
}

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

data class PingResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val projectPath: String,
    val mpsHome: String,
    val environmentReady: Boolean = false,
    val logPath: String? = null,
    val ideaConfigPath: String? = null,
    val ideaSystemPath: String? = null,
)

interface DaemonProcessLauncher {
    fun ping(projectPath: Path, mpsHome: Path): PingResponse
    fun resave(projectPath: Path, mpsHome: Path, modelTarget: Path): ModelResaveResponse
}

class ProcessDaemonLauncher(
    private val environment: Map<String, String> = System.getenv(),
    private val timeout: Duration = Duration.ofSeconds(15),
) : DaemonProcessLauncher {
    override fun ping(projectPath: Path, mpsHome: Path): PingResponse {
        return ensureDaemon(projectPath, mpsHome).ping
    }

    override fun resave(projectPath: Path, mpsHome: Path, modelTarget: Path): ModelResaveResponse {
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
}

private data class DaemonReady(
    val record: DaemonRecord,
    val ping: PingResponse,
)

data class DaemonLaunch(
    val projectPath: Path,
    val mpsHome: Path,
    val stateDir: Path,
    val workDir: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
    val recordPath: Path,
    val jvmArgs: List<String>,
) {
    companion object {
        fun prepare(projectPath: Path, mpsHome: Path, environment: Map<String, String>): DaemonLaunch {
            val normalizedProject = projectPath.absolute().normalize()
            val normalizedMpsHome = mpsHome.absolute().normalize()
            val projectState = daemonBaseDir(environment)
                .resolve("projects")
                .resolve(sha256(normalizedProject.pathString))
            val workDir = projectState.resolve("daemon")
            val ideaConfigDir = workDir.resolve("idea-config")
            val ideaSystemDir = workDir.resolve("idea-system")
            val logDir = projectState.resolve("logs").createDirectories()
            workDir.createDirectories()
            ideaConfigDir.createDirectories()
            ideaSystemDir.createDirectories()
            val logPath = logDir.resolve("daemon-ping.log")
            val recordPath = DaemonRecordStore(environment).recordPath(normalizedProject)

            return DaemonLaunch(
                projectPath = normalizedProject,
                mpsHome = normalizedMpsHome,
                stateDir = projectState,
                workDir = workDir,
                ideaConfigDir = ideaConfigDir,
                ideaSystemDir = ideaSystemDir,
                logPath = logPath,
                recordPath = recordPath,
                jvmArgs = MpsJvmArgs.forMpsHome(normalizedMpsHome, ideaConfigDir, ideaSystemDir),
            )
        }

        private fun daemonBaseDir(environment: Map<String, String>): Path =
            environment["MOPS_DAEMON_HOME"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it).absolute().normalize() }
                ?: Path.of(System.getProperty("user.home"), ".mops", "daemon").absolute().normalize()

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}

data class DaemonRecord(
    val port: Int,
    val token: String,
    val pid: Long,
    val protocolVersion: Int,
    val daemonVersion: String,
    val projectPath: String,
    val mpsHome: String,
    val logPath: String,
    val startupTime: String,
)

class DaemonRecordStore(
    private val environment: Map<String, String> = System.getenv(),
) {
    fun write(record: DaemonRecord) {
        val path = recordPath(Path.of(record.projectPath))
        path.parent.createDirectories()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, GsonCodec.toJson(record))
        Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun read(projectPath: Path): DaemonRecord? {
        val path = recordPath(projectPath)
        if (!Files.isRegularFile(path)) {
            return null
        }
        return GsonCodec.fromJson(Files.readString(path), DaemonRecord::class.java)
    }

    fun readAll(): List<DaemonRecord> {
        val projectsDir = daemonBaseDir(environment).resolve("projects")
        if (!Files.isDirectory(projectsDir)) {
            return emptyList()
        }
        return Files.list(projectsDir).use { projects ->
            projects
                .map { it.resolve("daemon.json") }
                .filter { Files.isRegularFile(it) }
                .map { GsonCodec.fromJson(Files.readString(it), DaemonRecord::class.java) }
                .toList()
        }
    }

    fun delete(projectPath: Path) {
        Files.deleteIfExists(recordPath(projectPath))
    }

    fun recordPath(projectPath: Path): Path =
        daemonBaseDir(environment)
            .resolve("projects")
            .resolve(projectKey(projectPath))
            .resolve("daemon.json")

    companion object {
        fun daemonBaseDir(environment: Map<String, String>): Path =
            environment["MOPS_DAEMON_HOME"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it).absolute().normalize() }
                ?: Path.of(System.getProperty("user.home"), ".mops", "daemon").absolute().normalize()

        fun projectKey(projectPath: Path): String =
            sha256(projectPath.absolute().normalize().pathString)

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}

class DaemonClient(
    private val timeout: Duration = Duration.ofSeconds(5),
) {
    fun ping(record: DaemonRecord): PingResponse =
        exchange(
            record = record,
            request = DaemonControlRequest(
                type = "ping",
                protocolVersion = ProtocolVersion,
                token = record.token,
            ),
            responseType = PingResponse::class.java,
        )

    fun stop(record: DaemonRecord): DaemonControlResponse =
        exchange(
            record = record,
            request = DaemonControlRequest(
                type = "stop",
                protocolVersion = ProtocolVersion,
                token = record.token,
            ),
            responseType = DaemonControlResponse::class.java,
        )

    fun resave(record: DaemonRecord, modelTarget: Path): ModelResaveResponse =
        Socket(InetAddress.getLoopbackAddress(), record.port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(
                        GsonCodec.toJson(
                            DaemonControlRequest(
                                type = "model-resave",
                                protocolVersion = ProtocolVersion,
                                token = record.token,
                                modelTarget = modelTarget.pathString,
                            ),
                        ),
                    )
                    val responseLine = reader.readLine() ?: throw IllegalStateException("daemon closed connection")
                    GsonCodec.fromJson(responseLine, ModelResaveResponse::class.java)
                }
            }
        }

    private fun <T : Any> exchange(record: DaemonRecord, request: DaemonControlRequest, responseType: Class<T>): T =
        Socket(InetAddress.getLoopbackAddress(), record.port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(GsonCodec.toJson(request))
                    val responseLine = reader.readLine() ?: throw IllegalStateException("daemon closed connection")
                    val status = GsonCodec.fromJson(responseLine, DaemonControlResponse::class.java)
                    if (status.status != "ok") {
                        throw IllegalStateException(status.message ?: "daemon returned ${status.status}")
                    }
                    GsonCodec.fromJson(responseLine, responseType)
                }
            }
        }
}

data class DaemonControlRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
    val modelTarget: String? = null,
)

data class DaemonControlResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val message: String? = null,
)

data class ModelResaveResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val projectPath: String,
    val mpsHome: String,
    val modelTarget: String? = null,
    val logPath: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
)

object MpsJvmArgs {
    fun forMpsHome(mpsHome: Path, ideaConfigDir: Path, ideaSystemDir: Path): List<String> {
        val mpsVersion = mpsVersion(mpsHome)
        return buildList {
            add("-Didea.max.intellisense.filesize=100000")
            add("-Dmops.mps.home=${mpsHome.pathString}")
            add("-Didea.config.path=${ideaConfigDir.pathString}")
            add("-Didea.system.path=${ideaSystemDir.pathString}")
            if (mpsVersion != null && mpsVersion >= "2025.2") {
                add("-Didea.platform.prefix=MPS")
            }
            if (mpsVersion != null && mpsVersion >= "2023.3") {
                add("-Dintellij.platform.load.app.info.from.resources=true")
            }
            if (mpsVersion != null && mpsVersion >= "2022.3") {
                add("-Djna.boot.library.path=${jnaPath(mpsHome).pathString}")
            }
            addAll(mpsAddOpens())
        }
    }

    fun requiredJavaMajor(mpsHome: Path): Int {
        val version = mpsVersion(mpsHome)
        return when {
            version == null -> Runtime.version().feature()
            version >= "2025" -> 21
            version >= "2022" -> 17
            else -> 11
        }
    }

    private fun mpsVersion(mpsHome: Path): String? {
        val buildProperties = mpsHome.resolve("build.properties")
        if (!Files.isRegularFile(buildProperties)) {
            return null
        }
        return Files.newInputStream(buildProperties).use { input ->
            Properties().apply { load(input) }.getProperty("mps.build.number")
        }
    }

    private fun jnaPath(mpsHome: Path): Path {
        val base = mpsHome.resolve("lib/jna")
        val platformSpecific = base.resolve(System.getProperty("os.arch"))
        return if (Files.exists(base) && !Files.exists(platformSpecific)) base else platformSpecific
    }

    private fun mpsAddOpens(): List<String> =
        listOf(
            "java.base/java.io",
            "java.base/java.lang",
            "java.base/java.lang.reflect",
            "java.base/java.net",
            "java.base/java.nio",
            "java.base/java.nio.charset",
            "java.base/java.text",
            "java.base/java.time",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/java.util.concurrent.atomic",
            "java.base/jdk.internal.ref",
            "java.base/jdk.internal.vm",
            "java.base/sun.nio.ch",
            "java.base/sun.nio.fs",
            "java.base/sun.security.ssl",
            "java.base/sun.security.util",
            "java.desktop/java.awt",
            "java.desktop/java.awt.dnd.peer",
            "java.desktop/java.awt.event",
            "java.desktop/java.awt.image",
            "java.desktop/java.awt.peer",
            "java.desktop/javax.swing",
            "java.desktop/javax.swing.plaf.basic",
            "java.desktop/javax.swing.text.html",
            "java.desktop/sun.awt.datatransfer",
            "java.desktop/sun.awt.image",
            "java.desktop/sun.awt",
            "java.desktop/sun.font",
            "java.desktop/sun.java2d",
            "java.desktop/sun.swing",
            "jdk.attach/sun.tools.attach",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.internal.jvmstat/sun.jvmstat.monitor",
            "jdk.jdi/com.sun.tools.jdi",
            "java.desktop/sun.lwawt",
            "java.desktop/sun.lwawt.macosx",
            "java.desktop/com.apple.laf",
            "java.desktop/com.apple.eawt",
            "java.desktop/com.apple.eawt.event",
            "java.management/sun.management",
        ).map { "--add-opens=$it=ALL-UNNAMED" }
}

private data class ReadyMessage(
    val type: String,
    val protocolVersion: Int,
    val port: Int,
)

private data class PingRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
)
