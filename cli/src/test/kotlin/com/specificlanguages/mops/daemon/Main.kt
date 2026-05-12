package com.specificlanguages.mops.daemon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    val logPath = args.asSequence()
        .windowed(2)
        .firstOrNull { it[0] == "--log-path" }
        ?.get(1)
        ?.let { Path.of(it) }
        ?: error("missing --log-path")
    logPath.parent.createDirectories()
    Files.writeString(logPath.parent.resolve("fake-daemon.pid"), ProcessHandle.current().pid().toString())
    println("""{"type":"ready","protocolVersion":999,"port":1}""")
    System.out.flush()
    Thread.sleep(Long.MAX_VALUE)
}
