package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.absolute
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

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
class MopsCommand(
    val launcher: DaemonProcessLauncher = ProcessDaemonLauncher(),
    val environment: Map<String, String> = System.getenv(),
    val workingDirectory: Path = Path.of("").absolute(),
) : Runnable {
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
