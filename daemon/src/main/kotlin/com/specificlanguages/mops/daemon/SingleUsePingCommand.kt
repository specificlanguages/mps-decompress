package com.specificlanguages.mops.daemon

import java.nio.file.Path
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "single-use-ping", description = ["Serve one loopback ping request, then exit."])
class SingleUsePingCommand : Runnable {
    @Option(names = ["--project-path"], required = true)
    lateinit var projectPath: String

    @Option(names = ["--token"], required = true)
    lateinit var token: String

    @Option(names = ["--idea-config-dir"], required = true)
    lateinit var ideaConfigDir: String

    @Option(names = ["--idea-system-dir"], required = true)
    lateinit var ideaSystemDir: String

    @Option(names = ["--log-path"], required = true)
    lateinit var logPath: String

    override fun run() {
        val runtime = MpsRuntimeBootstrap(
            projectPath = Path.of(projectPath),
            mpsHome = resolveMpsHomeFromRuntime(),
            ideaConfigDir = Path.of(ideaConfigDir),
            ideaSystemDir = Path.of(ideaSystemDir),
            logPath = Path.of(logPath),
        )
        try {
            runtime.withLoadedProject { environment ->
                SingleUsePingServer(
                    environment = environment,
                    expectedToken = token,
                ).serveOnce { ready ->
                    println(GsonCodec.toJson(ready))
                    System.out.flush()
                }
            }
        } catch (exception: RuntimeException) {
            runtime.log("startup failed: ${exception.message}")
            throw exception
        }
    }
}
