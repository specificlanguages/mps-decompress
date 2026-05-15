package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
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
        val mpsHome = requireMpsHome(root, spec.commandLine(), "daemon ping")
        val projectPath = requireProjectPath(spec.commandLine(), root.workingDirectory)

        val response = root.launcher.ping(projectPath, mpsHome, resolveJavaHome(root))
        spec.commandLine().out.println(GsonCodec.toJson(response))
    }
}
