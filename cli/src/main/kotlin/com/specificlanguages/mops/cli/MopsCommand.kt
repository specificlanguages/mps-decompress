package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.absolute
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Root CLI command shared by all daemon-backed operations.
 *
 * The root owns process-launch dependencies and global options so subcommands can be tested with fake launchers and
 * explicit environment maps instead of reading process globals directly.
 */
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

    @Option(
        names = ["--java-home"],
        paramLabel = "PATH",
        description = ["Java home used to start daemon-backed commands."],
    )
    var javaHome: String? = null

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
