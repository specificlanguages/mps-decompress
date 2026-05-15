package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.PingResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.assertTrue

internal fun Path.mpsProject(name: String = "project"): Path {
    val project = resolve(name).createDirectories()
    project.resolve(".mps").createDirectory()
    return project
}

internal fun daemonRecord(
    project: Path,
    port: Int,
    token: String = "secret",
    pid: Long = 1234,
    mpsHome: String,
    logPath: String,
    startupTime: String = "2026-05-12T12:00:00Z",
): DaemonRecord =
    DaemonRecord(
        port = port,
        token = token,
        pid = pid,
        protocolVersion = 1,
        daemonVersion = "0.3.0-SNAPSHOT",
        projectPath = project.pathString,
        mpsHome = mpsHome,
        logPath = logPath,
        startupTime = startupTime,
    )

internal fun daemonEnvironment(daemonHome: Path, vararg entries: Pair<String, String>): Map<String, String> =
    buildMap {
        put("MOPS_DAEMON_HOME", daemonHome.pathString)
        putAll(entries)
    }

internal fun startOneShotDaemon(response: String): OneShotDaemon {
    val serverReady = CountDownLatch(1)
    val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val daemon = OneShotDaemon(server.localPort)
    daemon.thread = Thread {
        server.use {
            serverReady.countDown()
            it.accept().use { socket ->
                daemon.requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                PrintWriter(socket.getOutputStream(), true).println(response)
            }
        }
    }
    daemon.thread.start()
    assertTrue(serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
    return daemon
}

internal class OneShotDaemon(
    val port: Int,
) {
    lateinit var thread: Thread
    lateinit var requestLine: String

    fun join() {
        thread.join(5_000)
    }
}

internal class RecordingLauncher : DaemonProcessLauncher {
    var projectPath: Path? = null
    var mpsHome: Path? = null
    var javaHome: Path? = null
    var modelTarget: Path? = null

    override fun ping(projectPath: Path, mpsHome: Path, javaHome: Path?): PingResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        this.javaHome = javaHome
        return PingResponse(
            protocolVersion = 1,
            projectPath = projectPath.pathString,
            mpsHome = mpsHome.pathString,
            environmentReady = true,
        )
    }

    override fun resave(projectPath: Path, mpsHome: Path, javaHome: Path?, modelTarget: Path): ModelResaveResponse {
        this.projectPath = projectPath
        this.mpsHome = mpsHome
        this.javaHome = javaHome
        this.modelTarget = modelTarget
        return ModelResaveResponse(
            protocolVersion = 1,
            modelTarget = modelTarget.pathString,
        )
    }
}
