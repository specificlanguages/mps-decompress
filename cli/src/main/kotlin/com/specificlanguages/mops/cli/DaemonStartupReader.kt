package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.ReadyMessage
import java.io.BufferedReader
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.pathString

internal class DaemonStartupReader(
    private val timeout: Duration,
) {
    fun readReadyMessage(reader: BufferedReader, process: Process, logPath: Path): ReadyMessage {
        val readyLine = readLineWithProcessCheck(reader, process, logPath)
        return parseStartupMessage(readyLine, logPath)
    }

    private fun readLineWithProcessCheck(reader: BufferedReader, process: Process, logPath: Path): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                return reader.readLine()
            }
            if (!process.isAlive) {
                throw daemonStartupException("daemon exited before reporting its socket port", logPath)
            }
            Thread.sleep(25)
        }
        throw daemonStartupException("timed out waiting for daemon socket port", logPath)
    }

    private fun parseStartupMessage(line: String, fallbackLogPath: Path): ReadyMessage {
        val message = GsonCodec.fromJson(line, DaemonResponse::class.java)
            ?: throw IllegalStateException("daemon did not report a startup message")
        if (message is DaemonErrorResponse) {
            val detail = listOf(message.errorCode, message.message).joinToString(": ")
                .ifBlank { "unknown daemon startup error" }
            val logPath = message.logPath?.let { Path.of(it) } ?: fallbackLogPath
            throw daemonStartupException("daemon startup failed: $detail", logPath)
        }
        if (message !is ReadyMessage) {
            throw IllegalStateException("daemon did not report a compatible ready message")
        }
        return message
    }

    private fun daemonStartupException(message: String, logPath: Path): IllegalStateException =
        IllegalStateException("$message. Daemon log: ${logPath.pathString}")
}
