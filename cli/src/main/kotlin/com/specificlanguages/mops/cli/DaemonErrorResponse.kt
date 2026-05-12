package com.specificlanguages.mops.cli

data class DaemonErrorResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val errorCode: String,
    val message: String,
    val logPath: String? = null,
)
