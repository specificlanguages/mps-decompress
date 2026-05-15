package com.specificlanguages.mops.protocol

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Filesystem-backed registry of daemon records.
 *
 * Records live outside the MPS project under the daemon state directory. The project path is hashed into the directory
 * name so the CLI can find, reuse, stop, or discard project-specific daemons without scanning process tables.
 */
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
        projectStateDir(projectPath).resolve("daemon.json")

    fun projectStateDir(projectPath: Path): Path =
        daemonBaseDir(environment)
            .resolve("projects")
            .resolve(projectKey(projectPath))

    companion object {
        fun daemonBaseDir(environment: Map<String, String>): Path =
            environment["MOPS_DAEMON_HOME"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it).absolute().normalize() }
                ?: Path.of(System.getProperty("user.home"), ".mops", "daemon")

        fun projectKey(projectPath: Path): String =
            sha256(projectPath.toRealPath().pathString)

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}
