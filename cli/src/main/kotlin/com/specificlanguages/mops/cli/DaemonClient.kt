package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonControlRequest
import com.specificlanguages.mops.protocol.DaemonControlResponse
import com.specificlanguages.mops.protocol.DaemonErrorResponse
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

    fun resave(record: DaemonRecord, modelTarget: Path): DaemonResponse =
        Socket(InetAddress.getLoopbackAddress(), record.port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(
                        GsonCodec.toJson(
                            ModelResaveRequest(
                                protocolVersion = ProtocolVersion,
                                token = record.token,
                                modelTarget = modelTarget.pathString,
                            ),
                        ),
                    )
                    val responseLine = reader.readLine() ?: throw IllegalStateException("daemon closed connection")
                    val status = GsonCodec.fromJson(responseLine, DaemonControlResponse::class.java)
                    if (status.status == "ok") {
                        GsonCodec.fromJson(responseLine, ModelResaveResponse::class.java)
                    } else {
                        GsonCodec.fromJson(responseLine, DaemonErrorResponse::class.java)
                    }
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
