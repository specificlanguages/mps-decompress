package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess
import picocli.CommandLine

fun main(args: Array<String>) {
    exitProcess(newCommandLine().execute(*args))
}

fun newCommandLine(
    launcher: DaemonProcessLauncher = ProcessDaemonLauncher(),
    environment: Map<String, String> = System.getenv(),
    workingDirectory: Path = Path.of("").absolute(),
): CommandLine =
    CommandLine(MopsCommand(launcher, environment, workingDirectory))
        .setExecutionExceptionHandler { exception, commandLine, _ ->
            commandLine.err.println(exception.message ?: exception::class.java.name)
            1
        }
