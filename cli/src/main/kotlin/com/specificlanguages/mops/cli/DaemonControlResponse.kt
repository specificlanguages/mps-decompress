package com.specificlanguages.mops.cli

data class DaemonControlResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val message: String? = null,
)
