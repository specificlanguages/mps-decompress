package com.specificlanguages.mops.cli

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

internal class DaemonClasspath(
    private val environment: Map<String, String>,
) {
    fun daemonClasspath(): String =
        environment["MOPS_DAEMON_CLASSPATH"]?.takeIf { it.isNotBlank() }
            ?: System.getProperty("mops.daemon.classpath")?.takeIf { it.isNotBlank() }
            ?: discoverLocalDaemonClasspath()
            ?: throw IllegalStateException(
                "cannot start daemon: set MOPS_DAEMON_CLASSPATH to the daemon runtime classpath",
            )

    fun mpsRuntimeClasspath(mpsHome: Path): String =
        buildList {
            addAll(jarsIn(mpsHome.resolve("lib")))
            addAll(jarsIn(mpsHome.resolve("lib/modules")))
        }.joinToString(File.pathSeparator)

    private fun jarsIn(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }
        return Files.list(directory).use { entries ->
            entries
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
                .sorted()
                .map { it.pathString }
                .toList()
        }
    }

    private fun discoverLocalDaemonClasspath(): String? =
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .asSequence()
            .mapNotNull { Path.of(it).toAbsolutePath().parent }
            .flatMap { parentsOf(it) }
            .flatMap {
                sequenceOf(
                    it.resolve("daemon/build/install/mops-daemon/lib"),
                    it.resolve("daemon/build/install/daemon/lib"),
                )
            }
            .firstOrNull { Files.isDirectory(it) }
            ?.let { libDir ->
                Files.list(libDir).use { entries ->
                    entries
                        .filter { it.fileName.toString().endsWith(".jar") }
                        .sorted()
                        .map { it.pathString }
                        .toList()
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(File.pathSeparator)
                }
            }

    private fun parentsOf(start: Path): Sequence<Path> = sequence {
        var current: Path? = start
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }
}
