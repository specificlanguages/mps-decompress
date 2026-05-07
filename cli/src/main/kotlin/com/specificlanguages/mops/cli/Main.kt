package com.specificlanguages.mops.cli

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
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

@Command(name = "ping", description = ["Start a single-use daemon and exchange a ping request."])
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
    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
    var all: Boolean = false

    override fun run() {
        println("mops daemon status is not implemented in this skeleton.")
    }
}

@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand : Runnable {
    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    override fun run() {
        println("mops daemon stop is not implemented in this skeleton.")
    }
}

@Command(
    name = "model",
    description = ["Run model operations through the mops daemon."],
    subcommands = [ModelResaveCommand::class],
)
class ModelCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "resave", description = ["Resave one model through the mops daemon."])
class ModelResaveCommand : Runnable {
    @Parameters(index = "0", paramLabel = "MODEL_TARGET", description = ["Persisted model path to resave."])
    lateinit var modelTarget: String

    override fun run() {
        println("mops model resave is not implemented in this skeleton: $modelTarget")
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
)

interface DaemonProcessLauncher {
    fun ping(projectPath: Path, mpsHome: Path): PingResponse
}

class ProcessDaemonLauncher(
    private val environment: Map<String, String> = System.getenv(),
    private val timeout: Duration = Duration.ofSeconds(15),
) : DaemonProcessLauncher {
    override fun ping(projectPath: Path, mpsHome: Path): PingResponse {
        val token = UUID.randomUUID().toString()
        val daemonClasspath = resolveDaemonClasspath()
        val process = ProcessBuilder(
            javaExecutable().pathString,
            "-cp",
            daemonClasspath,
            "com.specificlanguages.mops.daemon.MainKt",
            "single-use-ping",
            "--project-path",
            projectPath.absolute().normalize().pathString,
            "--mps-home",
            mpsHome.absolute().normalize().pathString,
            "--token",
            token,
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        try {
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val readyLine = readLineWithProcessCheck(stdout, process)
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

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("daemon did not exit after serving ping")
            }
            if (process.exitValue() != 0) {
                throw IllegalStateException("daemon exited with status ${process.exitValue()}")
            }
            if (response.status != "ok") {
                throw IllegalStateException("daemon ping failed with status ${response.status}")
            }
            return response
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
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

    private fun discoverLocalDaemonClasspath(): String? =
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .asSequence()
            .mapNotNull { Path.of(it).toAbsolutePath().parent }
            .flatMap { parentsOf(it) }
            .map { it.resolve("daemon/build/install/daemon/lib") }
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

    private fun readLineWithProcessCheck(reader: BufferedReader, process: Process): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                return reader.readLine()
            }
            if (!process.isAlive) {
                throw IllegalStateException("daemon exited before reporting its socket port")
            }
            Thread.sleep(25)
        }
        throw IllegalStateException("timed out waiting for daemon socket port")
    }
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
