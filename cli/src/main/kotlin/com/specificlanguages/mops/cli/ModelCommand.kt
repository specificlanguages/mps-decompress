package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for model-level operations that require a loaded MPS project.
 */
@Command(
    name = "model",
    description = ["Run model operations through the mops daemon."],
    subcommands = [ModelResaveCommand::class],
)
class ModelCommand : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
