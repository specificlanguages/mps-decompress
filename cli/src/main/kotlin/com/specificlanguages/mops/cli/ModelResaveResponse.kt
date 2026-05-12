package com.specificlanguages.mops.cli

data class ModelResaveResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val modelTarget: String? = null,
    val logPath: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
)
