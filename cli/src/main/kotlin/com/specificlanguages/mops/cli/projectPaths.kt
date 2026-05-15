package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import picocli.CommandLine

fun resolveMpsHome(cliValue: String?, environment: Map<String, String>): String? =
    cliValue?.takeIf { it.isNotBlank() }
        ?: environment["MOPS_MPS_HOME"]?.takeIf { it.isNotBlank() }

fun requireMpsHome(root: MopsCommand, commandLine: CommandLine, commandName: String): Path =
    resolveMpsHome(root.mpsHome, root.environment)
        ?.let { Path.of(it).absolute() }
        ?: throw CommandLine.ParameterException(
            commandLine,
            "$commandName requires MPS home; pass --mps-home <path> or set MOPS_MPS_HOME",
        )

fun resolveJavaHome(root: MopsCommand): Path? =
    root.javaHome?.takeIf { it.isNotBlank() }?.let { Path.of(it).absolute() }

fun requireProjectPath(
    commandLine: CommandLine,
    start: Path,
    displayPath: Path = start,
    messagePrefix: String = "cannot infer MPS project",
): Path =
    inferProjectPath(start)
        ?: throw CommandLine.ParameterException(
            commandLine,
            "$messagePrefix: no .mps directory found from ${displayPath.absolute()} upward",
        )

fun selectDaemonRecords(
    all: Boolean,
    root: MopsCommand,
    commandLine: CommandLine,
    records: DaemonRecordStore,
): List<DaemonRecord> =
    if (all) {
        records.readAll()
    } else {
        listOfNotNull(records.read(requireProjectPath(commandLine, root.workingDirectory)))
    }

fun inferProjectPath(start: Path): Path? {
    var current: Path? = start.absolute().normalize()
    while (current != null) {
        if (current.resolve(".mps").exists() && current.resolve(".mps").isDirectory()) {
            return current
        }
        current = current.parent
    }
    return null
}
