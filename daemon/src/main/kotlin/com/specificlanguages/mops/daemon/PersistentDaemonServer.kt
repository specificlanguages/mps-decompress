package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonControlResponse
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRequestEnvelope
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import com.specificlanguages.mops.protocol.ReadyMessage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.io.path.pathString

class PersistentDaemonServer(
    private val environment: MpsEnvironmentState,
    private val expectedToken: String,
    private val protocolVersion: Int = ProtocolVersion,
    private val idleTimeout: Duration = Duration.ofMinutes(3),
) {
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
        val envelope = try {
            GsonCodec.fromJson(requestLine, DaemonRequestEnvelope::class.java)
        } catch (_: RuntimeException) {
            return error("error", "INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }

        if (envelope == null) {
            return error("error", "INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }
        val requestType = envelope.type
        if (requestType.isNullOrBlank()) {
            return error("error", "INVALID_REQUEST", "request type is required")
        }
        if (envelope.protocolVersion != protocolVersion) {
            return error(requestType, "PROTOCOL_MISMATCH", "unsupported protocol version ${envelope.protocolVersion}")
        }
        if (envelope.token != expectedToken) {
            return error(requestType, "TOKEN_MISMATCH", "invalid daemon token")
        }

        return when (requestType) {
            "ping" -> PingResponse(
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
            "stop" -> DaemonControlResponse(
                type = "stop",
                status = "ok",
                protocolVersion = protocolVersion,
            )
            "model-resave" -> resaveModel(modelResaveRequest(requestLine ?: ""))
            else -> error(requestType, "INVALID_REQUEST", "unsupported request type $requestType")
        }
    }

    private fun modelResaveRequest(requestLine: String): ModelResaveRequest? =
        try {
            GsonCodec.fromJson(requestLine, ModelResaveRequest::class.java)
        } catch (_: RuntimeException) {
            null
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
}
