package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import com.specificlanguages.mops.protocol.ReadyMessage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.io.path.pathString

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
