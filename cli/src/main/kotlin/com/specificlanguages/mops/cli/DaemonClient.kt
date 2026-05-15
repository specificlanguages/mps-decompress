package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PingResponse
import com.specificlanguages.mops.protocol.ProtocolVersion
import com.specificlanguages.mops.protocol.StopRequest
import com.specificlanguages.mops.protocol.StopResponse
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
            request = PingRequest(
                protocolVersion = ProtocolVersion,
                token = token,
            ),
            responseType = PingResponse::class.java,
        )

    fun stop(record: DaemonRecord): StopResponse =
        exchange(
            port = record.port,
            request = StopRequest(
                protocolVersion = ProtocolVersion,
                token = record.token,
            ),
            responseType = StopResponse::class.java,
        )

    fun resave(record: DaemonRecord, modelTarget: Path): DaemonResponse =
        GsonCodec.fromJson(
            exchangeLine(
                port = record.port,
                request = ModelResaveRequest(
                    protocolVersion = ProtocolVersion,
                    token = record.token,
                    modelTarget = modelTarget.pathString,
                ),
            ),
            DaemonResponse::class.java,
        )

    private fun <T : DaemonResponse> exchange(port: Int, request: DaemonRequest, responseType: Class<T>): T {
        val response = GsonCodec.fromJson(exchangeLine(port, request), DaemonResponse::class.java)
        if (response.status != "ok") {
            val message = if (response is DaemonErrorResponse) {
                response.message
            } else {
                "daemon returned ${response.status}"
            }
            throw IllegalStateException(message)
        }
        if (!responseType.isInstance(response)) {
            throw IllegalStateException("daemon returned unexpected response type ${response.type}")
        }
        return responseType.cast(response)
    }

    private fun exchangeLine(port: Int, request: DaemonRequest): String =
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
