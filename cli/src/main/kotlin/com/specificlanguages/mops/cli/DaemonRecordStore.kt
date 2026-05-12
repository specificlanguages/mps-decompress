package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

class DaemonRecordStore(
    private val environment: Map<String, String> = System.getenv(),
) {
    fun write(record: DaemonRecord) {
        val path = recordPath(Path.of(record.projectPath))
        path.parent.createDirectories()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, GsonCodec.toJson(record))
        Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun read(projectPath: Path): DaemonRecord? {
        val path = recordPath(projectPath)
        if (!Files.isRegularFile(path)) {
            return null
        }
        return GsonCodec.fromJson(Files.readString(path), DaemonRecord::class.java)
    }

    fun readAll(): List<DaemonRecord> {
        val projectsDir = daemonBaseDir(environment).resolve("projects")
        if (!Files.isDirectory(projectsDir)) {
            return emptyList()
        }
        return Files.list(projectsDir).use { projects ->
            projects
                .map { it.resolve("daemon.json") }
                .filter { Files.isRegularFile(it) }
                .map { GsonCodec.fromJson(Files.readString(it), DaemonRecord::class.java) }
                .toList()
        }
    }

    fun delete(projectPath: Path) {
        Files.deleteIfExists(recordPath(projectPath))
    }

    fun recordPath(projectPath: Path): Path =
        daemonBaseDir(environment)
            .resolve("projects")
            .resolve(projectKey(projectPath))
            .resolve("daemon.json")

    companion object {
        fun daemonBaseDir(environment: Map<String, String>): Path =
            environment["MOPS_DAEMON_HOME"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it).absolute().normalize() }
                ?: Path.of(System.getProperty("user.home"), ".mops", "daemon").absolute().normalize()

        fun projectKey(projectPath: Path): String =
            sha256(projectPath.absolute().normalize().pathString)

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}
