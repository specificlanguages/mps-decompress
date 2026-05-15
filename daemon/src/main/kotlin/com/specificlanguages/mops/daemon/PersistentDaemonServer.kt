package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.ProtocolVersion
import com.specificlanguages.mops.protocol.ReadyMessage
import com.specificlanguages.mops.protocol.StopRequest
import com.specificlanguages.mops.protocol.StopResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.io.path.pathString

class PersistentDaemonServer(
    private val session: MpsProjectSession,
    private val expectedToken: String,
    private val protocolVersion: Int = ProtocolVersion,
    private val idleTimeout: Duration = Duration.ofMinutes(3),
) {
    private val environment: MpsEnvironmentState = session.environment

    fun serve(onReady: (ReadyMessage) -> Unit = {}) {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = idleTimeout.toMillis().toInt()
            onReady(
                ReadyMessage(
                    type = "ready",
                    protocolVersion = protocolVersion,
                    port = server.localPort,
                ),
            )

            var stopping = false
            while (!stopping) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    break
                }
                socket.use {
                    val request = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    val response = handle(request)
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(GsonCodec.toJson(response))
                    }
                    if (response.type == "stop" && response.status == "ok") {
                        stopping = true
                    }
                }
            }
        }
    }

    fun handle(requestLine: String?): DaemonResponse {
        val request = try {
            GsonCodec.fromJson(requestLine, DaemonRequest::class.java)
        } catch (exception: RuntimeException) {
            return error("error", "INVALID_REQUEST", invalidRequestMessage(exception))
        }

        if (request == null) {
            return error("error", "INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }
        val requestType = request.type
        if (request.protocolVersion != protocolVersion) {
            return error(requestType, "PROTOCOL_MISMATCH", "unsupported protocol version ${request.protocolVersion}")
        }
        if (request.token != expectedToken) {
            return error(requestType, "TOKEN_MISMATCH", "invalid daemon token")
        }

        return when (request) {
            is PingRequest -> PingResponse(
                protocolVersion = protocolVersion,
                projectPath = environment.projectPath.pathString,
                mpsHome = environment.mpsHome.pathString,
                environmentReady = true,
                logPath = environment.logPath.pathString,
                ideaConfigPath = environment.ideaConfigDir.pathString,
                ideaSystemPath = environment.ideaSystemDir.pathString,
            )
            is StopRequest -> StopResponse(
                protocolVersion = protocolVersion,
            )
            is ModelResaveRequest -> resaveModel(request)
        }
    }

    private fun resaveModel(request: ModelResaveRequest?): DaemonResponse {
        if (request?.modelTarget.isNullOrBlank()) {
            return error("model-resave", "INVALID_REQUEST", "modelTarget is required", environment.logPath.pathString)
        }

        // Put the MPS API model lookup and save call here.
        return error(
            type = "model-resave",
            code = "NOT_IMPLEMENTED",
            message = "model resave is routed through the MPS daemon, but the MPS API resave implementation is not wired yet",
            logPath = environment.logPath.pathString,
        )
    }

    private fun error(type: String, code: String, message: String, logPath: String? = null): DaemonErrorResponse =
        DaemonErrorResponse(
            type = type,
            status = "error",
            protocolVersion = protocolVersion,
            errorCode = code,
            message = message,
            logPath = logPath,
        )

    private fun invalidRequestMessage(exception: RuntimeException): String =
        exception.message
            ?.takeIf { it == "request type is required" || it.startsWith("unsupported request type ") }
            ?: "request must be one newline-delimited JSON object"
}
