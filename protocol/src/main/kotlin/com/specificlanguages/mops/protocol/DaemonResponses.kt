package com.specificlanguages.mops.protocol

sealed interface DaemonResponse {
    val type: String
    val status: String
    val protocolVersion: Int
}

data class PingResponse(
    override val type: String = "ping",
    override val status: String = "ok",
    override val protocolVersion: Int,
    val projectPath: String,
    val mpsHome: String,
    val environmentReady: Boolean = false,
    val logPath: String? = null,
    val ideaConfigPath: String? = null,
    val ideaSystemPath: String? = null,
) : DaemonResponse

data class StopResponse(
    override val type: String = "stop",
    override val status: String = "ok",
    override val protocolVersion: Int,
    val message: String? = null,
) : DaemonResponse

data class DaemonErrorResponse(
    override val type: String,
    override val status: String = "error",
    override val protocolVersion: Int,
    val errorCode: String,
    val message: String,
    val logPath: String? = null,
) : DaemonResponse

data class ModelResaveResponse(
    override val type: String = "model-resave",
    override val status: String = "ok",
    override val protocolVersion: Int,
    val modelTarget: String,
) : DaemonResponse

data class ReadyMessage(
    override val type: String = "ready",
    override val status: String = "ok",
    override val protocolVersion: Int,
    val port: Int,
) : DaemonResponse
