package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Path
import kotlin.io.path.absolute
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    override fun run() {
        val root = daemon.root
        val records = DaemonRecordStore(root.environment)
        val selected = if (all) {
            records.readAll()
        } else {
            val projectPath = inferProjectPath(root.workingDirectory)
                ?: throw CommandLine.ParameterException(
                    spec.commandLine(),
                    "cannot infer MPS project: no .mps directory found from ${root.workingDirectory.absolute()} upward",
                )
            listOfNotNull(records.read(projectPath))
        }

        if (selected.isEmpty()) {
            spec.commandLine().out.println("no mops daemons")
            return
        }

        selected.forEach { record: DaemonRecord ->
            try {
                DaemonClient().stop(record)
                records.delete(Path.of(record.projectPath))
                spec.commandLine().out.println("stopped project=${record.projectPath} pid=${record.pid}")
            } catch (_: Exception) {
                records.delete(Path.of(record.projectPath))
                spec.commandLine().out.println("removed stale daemon record for project=${record.projectPath}")
            }
        }
    }
}
