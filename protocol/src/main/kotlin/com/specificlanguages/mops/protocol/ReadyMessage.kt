package com.specificlanguages.mops.protocol

data class ReadyMessage(
    val type: String,
    val protocolVersion: Int,
    val port: Int,
)
