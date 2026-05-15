package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.ModelResaveResponse
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import jetbrains.mps.extapi.persistence.FileDataSource
import kotlin.io.path.pathString
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import org.jetbrains.mps.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Single-project daemon server for authenticated loopback protocol requests.
 *
 * The server owns the request loop and delegates model operations to the already opened MPS [Project]. Requests are
 * processed serially, which keeps MPS model access simple and makes each daemon an isolated owner of one project.
 */
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

    private fun resaveModel(request: ModelResaveRequest): DaemonResponse {
        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            return error("model-resave", "INVALID_REQUEST", "modelTarget is required", environment.logPath.pathString)
        }
        val project = session.project

        val future = CompletableFuture<ModelResaveResponse>()

        project.modelAccess.executeCommandInEDT {
            try {
                project.modelAccess.computeWriteAction {
                    val model = findModel(project, modelTarget)
                        ?: return@computeWriteAction error(
                            type = "model-resave",
                            code = "MODEL_NOT_FOUND",
                            message = "model not found: $modelTarget",
                            logPath = environment.logPath.pathString,
                        )
                    if (model.isReadOnly || model !is EditableSModel) {
                        return@computeWriteAction error(
                            type = "model-resave",
                            code = "MODEL_READ_ONLY",
                            message = "model is not editable: ${model.name.longName}",
                            logPath = environment.logPath.pathString,
                        )
                    }

                    model.load()
                    val result = model.save(SaveOptions.FORCE_SAVE_WITH_RESOLVE_INFO).toCompletableFuture().join()
                    if (result != SaveResult.SAVED_TO_DATA_SOURCE && result != SaveResult.NOT_CHANGED) {
                        return@computeWriteAction error(
                            type = "model-resave",
                            code = "SAVE_FAILED",
                            message = "model save failed for ${model.name.longName}: $result",
                            logPath = environment.logPath.pathString,
                        )
                    }
                }
                future.complete(ModelResaveResponse(protocolVersion = protocolVersion, modelTarget = modelTarget))
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }

        return try {
            future.get()
        } catch (exception: Exception) {
            val cause = if (exception is ExecutionException) exception.cause else exception
            error(
                type = "model-resave",
                code = "SAVE_FAILED",
                message = cause?.message
                    ?: cause?.javaClass?.name
                    ?: exception.message
                    ?: exception.javaClass.name,
                logPath = environment.logPath.pathString,
            )
        }
    }

    private fun findModel(project: Project, modelTarget: String): SModel? {
        val targetPath = targetPath(modelTarget)
        val candidates = modelCandidates(project).toList()
        val model = candidates
            .firstOrNull { model ->
                model.name.longName == modelTarget ||
                    model.name.value == modelTarget ||
                    targetPath != null && model.filePath() == targetPath
            }
        if (model == null) {
            log(
                "model target $modelTarget not found among ${candidates.size} models: " +
                    candidates.take(20).joinToString { "${it.name.longName} [${it.filePath()}]" },
            )
        }
        return model
    }

    private fun modelCandidates(project: Project): Sequence<SModel> =
        (
            project.projectModules.asSequence().flatMap { it.models.asSequence() } +
                project.repository.modules.asSequence().flatMap { it.models.asSequence() }
        ).distinctBy { it.reference }

    private fun targetPath(modelTarget: String): Path? =
        runCatching {
            Path.of(modelTarget).let { path ->
                if (Files.exists(path)) {
                    path.toRealPath()
                } else {
                    path.toAbsolutePath().normalize()
                }
            }
        }.getOrNull()

    private fun SModel.filePath(): Path? {
        val dataSource = source
        if (dataSource is FileDataSource) {
            return runCatching { Path.of(dataSource.file.toRealPath()).toAbsolutePath().normalize() }.getOrNull()
        }
        return runCatching { Path.of(dataSource.location).toAbsolutePath().normalize() }.getOrNull()
    }

    private fun log(message: String) {
        Files.writeString(
            environment.logPath,
            "${Instant.now()} $message\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
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
