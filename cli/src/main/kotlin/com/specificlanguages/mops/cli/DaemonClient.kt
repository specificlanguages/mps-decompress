package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonControlRequest
import com.specificlanguages.mops.protocol.DaemonControlResponse
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.pathString

class DaemonClient(
    private val timeout: Duration = Duration.ofSeconds(5),
) {
    fun ping(record: DaemonRecord): PingResponse =
        ping(record.port, record.token)

    fun ping(port: Int, token: String): PingResponse =
        exchange(
            port = port,
            request = DaemonControlRequest(
                type = "ping",
                protocolVersion = ProtocolVersion,
                token = token,
            ),
            responseType = PingResponse::class.java,
        )

    fun stop(record: DaemonRecord): DaemonControlResponse =
        exchange(
            port = record.port,
            request = DaemonControlRequest(
                type = "stop",
                protocolVersion = ProtocolVersion,
                token = record.token,
            ),
            responseType = DaemonControlResponse::class.java,
        )

    fun resave(record: DaemonRecord, modelTarget: Path): DaemonResponse =
        decodeModelResaveResponse(
            exchangeLine(
                port = record.port,
                request = ModelResaveRequest(
                    protocolVersion = ProtocolVersion,
                    token = record.token,
                    modelTarget = modelTarget.pathString,
                ),
            ),
        )

    private fun decodeModelResaveResponse(responseLine: String): DaemonResponse {
        val status = GsonCodec.fromJson(responseLine, DaemonControlResponse::class.java)
        return if (status.status == "ok") {
            GsonCodec.fromJson(responseLine, ModelResaveResponse::class.java)
        } else {
            GsonCodec.fromJson(responseLine, DaemonErrorResponse::class.java)
        }
    }

    private fun <T : Any> exchange(port: Int, request: DaemonControlRequest, responseType: Class<T>): T {
        val responseLine = exchangeLine(port, request)
        val status = GsonCodec.fromJson(responseLine, DaemonControlResponse::class.java)
        if (status.status != "ok") {
            throw IllegalStateException(status.message ?: "daemon returned ${status.status}")
        }
        return GsonCodec.fromJson(responseLine, responseType)
    }

    private fun exchangeLine(port: Int, request: Any): String =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(GsonCodec.toJson(request))
                    reader.readLine() ?: throw IllegalStateException("daemon closed connection")
                }
            }
        }
}
