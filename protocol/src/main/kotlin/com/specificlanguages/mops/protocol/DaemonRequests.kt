package com.specificlanguages.mops.protocol

/**
 * Base contract for authenticated requests sent over the daemon socket.
 */
sealed interface DaemonRequest {
    val type: String
    val protocolVersion: Int
    val token: String
}

data class PingRequest(
    override val type: String = "ping",
    override val protocolVersion: Int,
    override val token: String,
) : DaemonRequest

data class StopRequest(
    override val type: String = "stop",
    override val protocolVersion: Int,
    override val token: String,
) : DaemonRequest

/**
 * Request to resave one model target inside the already loaded project daemon.
 */
data class ModelResaveRequest(
    override val type: String = "model-resave",
    override val protocolVersion: Int,
    override val token: String,
    val modelTarget: String?,
) : DaemonRequest
