package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
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

    override fun run() {
        SingleUsePingServer(
            projectPath = projectPath,
            mpsHome = mpsHome,
            expectedToken = token,
        ).serveOnce { ready ->
            println(GsonCodec.toJson(ready))
            System.out.flush()
        }
    }
}

class SingleUsePingServer(
    private val projectPath: String,
    private val mpsHome: String,
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
            projectPath = projectPath,
            mpsHome = mpsHome,
        )
    }

    private fun error(code: String, message: String): PingResponse =
        PingResponse(
            type = "ping",
            status = "error",
            protocolVersion = protocolVersion,
            projectPath = projectPath,
            mpsHome = mpsHome,
            errorCode = code,
            message = message,
        )
}

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
    val errorCode: String? = null,
    val message: String? = null,
)
