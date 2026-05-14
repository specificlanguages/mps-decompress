package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import kotlin.io.path.absolute
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(name = "status", description = ["Print daemon status."])
class DaemonStatusCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
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
            spec.commandLine().out.println(
                "running project=${record.projectPath} port=${record.port} pid=${record.pid} mpsHome=${record.mpsHome} log=${record.logPath}",
            )
        }
    }
}
