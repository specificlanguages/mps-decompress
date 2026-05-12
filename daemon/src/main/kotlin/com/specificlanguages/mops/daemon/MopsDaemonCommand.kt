package com.specificlanguages.mops.daemon

import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Daemon process skeleton for MPS-backed mops operations."],
    subcommands = [
        SingleUsePingCommand::class,
        ServeCommand::class,
    ],
)
class MopsDaemonCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
