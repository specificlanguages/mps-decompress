package com.specificlanguages.mops.daemon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logPath = args.asSequence()
        .windowed(2)
        .firstOrNull { it[0] == "--log-path" }
        ?.get(1)
        ?.let { Path.of(it) }
        ?: error("missing --log-path")
    logPath.parent.createDirectories()
    Files.writeString(logPath.parent.resolve("fake-daemon.pid"), ProcessHandle.current().pid().toString())
    if (System.getenv("MOPS_FAKE_DAEMON_STARTUP_ERROR") == "1") {
        println(
            """{"type":"error","status":"error","protocolVersion":1,"errorCode":"JVM_VERSION_MISMATCH","message":"daemon JVM 17 is not compatible with MPS 2025.1; required Java 21","logPath":"${logPath}"}""",
        )
        System.out.flush()
        exitProcess(1)
    }
    println("""{"type":"ready","protocolVersion":999,"port":1}""")
    System.out.flush()
    Thread.sleep(Long.MAX_VALUE)
}
