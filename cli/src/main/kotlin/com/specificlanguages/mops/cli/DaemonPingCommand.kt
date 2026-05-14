package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import java.nio.file.Path
import kotlin.io.path.absolute
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(name = "ping", description = ["Start or reuse a project daemon and exchange a ping request."])
class DaemonPingCommand : Runnable {
    @ParentCommand
    lateinit var daemon: DaemonCommand

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        val root = daemon.root
        val mpsHome = resolveMpsHome(root.mpsHome, root.environment)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "daemon ping requires MPS home; pass --mps-home <path> or set MOPS_MPS_HOME",
            )
        val projectPath = inferProjectPath(root.workingDirectory)
            ?: throw CommandLine.ParameterException(
                spec.commandLine(),
                "cannot infer MPS project: no .mps directory found from ${root.workingDirectory.absolute()} upward",
            )

        val javaHome = root.javaHome?.takeIf { it.isNotBlank() }?.let { Path.of(it).absolute() }
        val response = root.launcher.ping(projectPath, Path.of(mpsHome).absolute(), javaHome)
        spec.commandLine().out.println(GsonCodec.toJson(response))
    }
}
