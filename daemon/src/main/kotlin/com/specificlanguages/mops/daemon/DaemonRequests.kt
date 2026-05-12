package com.specificlanguages.mops.daemon

sealed interface DaemonRequest {
    val type: String
    val protocolVersion: Int
    val token: String
}

data class PingRequest(
    override val type: String,
    override val protocolVersion: Int,
    override val token: String,
) : DaemonRequest

data class DaemonControlRequest(
    override val type: String,
    override val protocolVersion: Int,
    override val token: String,
) : DaemonRequest

data class ModelResaveRequest(
    override val type: String = "model-resave",
    override val protocolVersion: Int,
    override val token: String,
    val modelTarget: String?,
) : DaemonRequest
