package com.specificlanguages.mops.protocol

data class DaemonRequestEnvelope(
    val type: String?,
    val protocolVersion: Int?,
    val token: String?,
)
