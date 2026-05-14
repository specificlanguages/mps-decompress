package com.specificlanguages.mops.protocol

/**
 * Contact information and metadata of a running mops daemon.
 */
data class DaemonRecord(
    // Contact information
    val port: Int,
    val token: String,
    val pid: Long,

    // Compatibility
    val protocolVersion: Int,
    val daemonVersion: String,

    // Applicability
    val projectPath: String,
    val mpsHome: String,

    // Runtime information
    val logPath: String,
    val startupTime: String,
)
