package com.specificlanguages.mops.cli

import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

fun main(args: Array<String>) {
    exitProcess(CommandLine(MopsCommand()).execute(*args))
}

@Command(
    name = "mops",
    mixinStandardHelpOptions = true,
    version = ["mops 0.3.0-SNAPSHOT"],
    description = ["Kotlin CLI for the daemon-backed MPS prototype."],
    subcommands = [
        DaemonCommand::class,
        ModelCommand::class,
    ],
)
class MopsCommand : Runnable {
    @Option(
        names = ["--mps-home"],
        paramLabel = "PATH",
        description = ["MPS home used by daemon-backed commands."],
    )
    var mpsHome: String? = null

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(
    name = "daemon",
    description = ["Inspect or control mops daemon processes."],
    subcommands = [
        DaemonStatusCommand::class,
        DaemonStopCommand::class,
    ],
)
class DaemonCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "status", description = ["Print daemon status."])
class DaemonStatusCommand : Runnable {
    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
    var all: Boolean = false

    override fun run() {
        println("mops daemon status is not implemented in this skeleton.")
    }
}

@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand : Runnable {
    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    override fun run() {
        println("mops daemon stop is not implemented in this skeleton.")
    }
}

@Command(
    name = "model",
    description = ["Run model operations through the mops daemon."],
    subcommands = [ModelResaveCommand::class],
)
class ModelCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "resave", description = ["Resave one model through the mops daemon."])
class ModelResaveCommand : Runnable {
    @Parameters(index = "0", paramLabel = "MODEL_TARGET", description = ["Persisted model path to resave."])
    lateinit var modelTarget: String

    override fun run() {
        println("mops model resave is not implemented in this skeleton: $modelTarget")
    }
}
