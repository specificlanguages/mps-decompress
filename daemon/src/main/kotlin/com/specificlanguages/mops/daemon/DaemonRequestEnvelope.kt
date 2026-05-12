package com.specificlanguages.mops.daemon

data class DaemonRequestEnvelope(
    val type: String?,
    val protocolVersion: Int,
    val token: String,
)
