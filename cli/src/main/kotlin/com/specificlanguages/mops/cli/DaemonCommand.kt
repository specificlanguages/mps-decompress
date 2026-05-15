package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for daemon lifecycle operations.
 */
@Command(
    name = "daemon",
    description = ["Inspect or control mops daemon processes."],
    subcommands = [
        DaemonPingCommand::class,
        DaemonStatusCommand::class,
        DaemonStopCommand::class,
    ],
)
class DaemonCommand : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
