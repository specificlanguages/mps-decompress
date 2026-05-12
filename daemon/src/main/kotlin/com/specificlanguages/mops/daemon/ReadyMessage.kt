package com.specificlanguages.mops.daemon

data class ReadyMessage(
    val type: String,
    val protocolVersion: Int,
    val port: Int,
)
