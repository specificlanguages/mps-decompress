package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.ModelResaveResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(name = "resave", description = ["Resave one model through the mops daemon."])
class ModelResaveCommand : Runnable {
    @ParentCommand
    lateinit var model: ModelCommand

    @Spec
    lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "MODEL_TARGET", description = ["Persisted model path to resave."])
    lateinit var modelTarget: String

    override fun run() {
        val root = model.root
        val mpsHome = requireMpsHome(root, spec.commandLine(), "model resave")
        val resolvedTarget = Path.of(modelTarget).absolute().normalize()
        val projectSearchStart = if (Files.isDirectory(resolvedTarget)) resolvedTarget else resolvedTarget.parent ?: resolvedTarget
        val projectPath = requireProjectPath(
            commandLine = spec.commandLine(),
            start = projectSearchStart,
            displayPath = resolvedTarget,
            messagePrefix = "cannot infer MPS project from model target",
        )

        val response = root.launcher.resave(projectPath, mpsHome, resolveJavaHome(root), resolvedTarget)
        when (response) {
            is ModelResaveResponse -> spec.commandLine().out.println("resaved ${response.modelTarget}")
            is DaemonErrorResponse -> {
                val logSuffix = response.logPath?.let { " Daemon log: $it" } ?: ""
                throw IllegalStateException("${response.message}$logSuffix")
            }
            else -> throw IllegalStateException("model resave returned unexpected response type ${response.type}")
        }
    }
}
