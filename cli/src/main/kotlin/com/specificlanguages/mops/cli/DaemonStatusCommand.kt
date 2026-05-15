package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

/**
 * Reads persisted daemon records and reports which project daemons are known locally.
 *
 * Status is intentionally record-based: it does not start a daemon or require an MPS home.
 */
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
        val selected = selectDaemonRecords(all, root, spec.commandLine(), records)

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
