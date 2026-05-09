package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

private const val ProtocolVersion = 1
private val GsonCodec = Gson()

fun main(args: Array<String>) {
    exitProcess(CommandLine(MopsDaemonCommand()).execute(*args))
}

@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Daemon process skeleton for MPS-backed mops operations."],
    subcommands = [SingleUsePingCommand::class],
)
class MopsDaemonCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "single-use-ping", description = ["Serve one loopback ping request, then exit."])
class SingleUsePingCommand : Runnable {
    @Option(names = ["--project-path"], required = true)
    lateinit var projectPath: String

    @Option(names = ["--mps-home"], required = true)
    lateinit var mpsHome: String

    @Option(names = ["--token"], required = true)
    lateinit var token: String

    @Option(names = ["--idea-config-dir"], required = true)
    lateinit var ideaConfigDir: String

    @Option(names = ["--idea-system-dir"], required = true)
    lateinit var ideaSystemDir: String

    @Option(names = ["--log-path"], required = true)
    lateinit var logPath: String

    override fun run() {
        val runtime = MpsRuntimeBootstrap(
            projectPath = Path.of(projectPath),
            mpsHome = Path.of(mpsHome),
            ideaConfigDir = Path.of(ideaConfigDir),
            ideaSystemDir = Path.of(ideaSystemDir),
            logPath = Path.of(logPath),
        )
        try {
            val environment = runtime.initialize()
            SingleUsePingServer(
                environment = environment,
                expectedToken = token,
            ).serveOnce { ready ->
                println(GsonCodec.toJson(ready))
                System.out.flush()
            }
        } catch (exception: RuntimeException) {
            runtime.log("startup failed: ${exception.message}")
            throw exception
        }
    }
}

class SingleUsePingServer(
    private val environment: MpsEnvironmentState,
    private val expectedToken: String,
    private val protocolVersion: Int = ProtocolVersion,
) {
    fun serveOnce(onReady: (ReadyMessage) -> Unit = {}) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            onReady(
                ReadyMessage(
                    type = "ready",
                    protocolVersion = protocolVersion,
                    port = server.localPort,
                ),
            )

            server.accept().use { socket ->
                val request = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                val response = handle(request)
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    writer.println(GsonCodec.toJson(response))
                }
            }
        }
    }

    fun handle(requestLine: String?): PingResponse {
        val request = try {
            GsonCodec.fromJson(requestLine, PingRequest::class.java)
        } catch (_: RuntimeException) {
            return error("INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }

        if (request == null || request.type != "ping") {
            return error("INVALID_REQUEST", "request type must be ping")
        }
        if (request.protocolVersion != protocolVersion) {
            return error("PROTOCOL_MISMATCH", "unsupported protocol version ${request.protocolVersion}")
        }
        if (request.token != expectedToken) {
            return error("TOKEN_MISMATCH", "invalid daemon token")
        }

        return PingResponse(
            type = "ping",
            status = "ok",
            protocolVersion = protocolVersion,
            projectPath = environment.projectPath.pathString,
            mpsHome = environment.mpsHome.pathString,
            environmentReady = true,
            logPath = environment.logPath.pathString,
            ideaConfigPath = environment.ideaConfigDir.pathString,
            ideaSystemPath = environment.ideaSystemDir.pathString,
        )
    }

    private fun error(code: String, message: String): PingResponse =
        PingResponse(
            type = "ping",
            status = "error",
            protocolVersion = protocolVersion,
            projectPath = environment.projectPath.pathString,
            mpsHome = environment.mpsHome.pathString,
            environmentReady = true,
            logPath = environment.logPath.pathString,
            ideaConfigPath = environment.ideaConfigDir.pathString,
            ideaSystemPath = environment.ideaSystemDir.pathString,
            errorCode = code,
            message = message,
        )
}

class MpsRuntimeBootstrap(
    private val projectPath: Path,
    private val mpsHome: Path,
    private val ideaConfigDir: Path,
    private val ideaSystemDir: Path,
    private val logPath: Path,
) {
    fun initialize(): MpsEnvironmentState {
        logPath.parent.createDirectories()
        log("initializing single-use MPS daemon runtime")
        requireDirectory(projectPath, "project path")
        requireDirectory(projectPath.resolve(".mps"), "MPS project marker")
        requireDirectory(mpsHome, "MPS home")
        requireFile(mpsHome.resolve("build.properties"), "MPS build properties")
        ideaConfigDir.createDirectories()
        ideaSystemDir.createDirectories()
        log("idea.config.path=${ideaConfigDir.pathString}")
        log("idea.system.path=${ideaSystemDir.pathString}")
        log("environment ready for project ${projectPath.pathString}")

        return MpsEnvironmentState(
            projectPath = projectPath,
            mpsHome = mpsHome,
            ideaConfigDir = ideaConfigDir,
            ideaSystemDir = ideaSystemDir,
            logPath = logPath,
        )
    }

    fun log(message: String) {
        logPath.parent.createDirectories()
        Files.writeString(
            logPath,
            "${Instant.now()} $message\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private fun requireDirectory(path: Path, label: String) {
        if (!Files.isDirectory(path)) {
            throw IllegalStateException("$label is not a directory: ${path.pathString}")
        }
    }

    private fun requireFile(path: Path, label: String) {
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("$label is missing: ${path.pathString}")
        }
    }
}

data class MpsEnvironmentState(
    val projectPath: Path,
    val mpsHome: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
)

data class ReadyMessage(
    val type: String,
    val protocolVersion: Int,
    val port: Int,
)

data class PingRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
)

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
    val errorCode: String? = null,
    val message: String? = null,
)
