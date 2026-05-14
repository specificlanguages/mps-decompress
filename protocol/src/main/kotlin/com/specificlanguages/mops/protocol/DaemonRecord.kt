package com.specificlanguages.mops.protocol

data class DaemonRecord(
    val port: Int,
    val token: String,
    val pid: Long,
    val protocolVersion: Int,
    val daemonVersion: String,
    val projectPath: String,
    val mpsHome: String,
    val logPath: String,
    val startupTime: String,
)
