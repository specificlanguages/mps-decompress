package com.specificlanguages.mops.cli

data class DaemonControlRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
    val modelTarget: String? = null,
)
