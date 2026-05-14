package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.GsonCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Serve loopback daemon requests until stopped or idle."],
)
class MopsDaemonCommand : Runnable {
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

    @Option(names = ["--record-path"], required = true)
    lateinit var recordPath: String

    @Option(names = ["--idle-timeout-ms"])
    var idleTimeoutMillis: Long = Duration.ofMinutes(3).toMillis()

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
                PersistentDaemonServer(
                    environment = environment,
                    expectedToken = token,
                    idleTimeout = Duration.ofMillis(idleTimeoutMillis),
                ).serve { ready ->
                    writeRecord(
                        path = Path.of(recordPath),
                        record = DaemonRecord(
                            port = ready.port,
                            token = token,
                            pid = ProcessHandle.current().pid(),
                            protocolVersion = ready.protocolVersion,
                            daemonVersion = "0.3.0-SNAPSHOT",
                            projectPath = environment.projectPath.pathString,
                            mpsHome = environment.mpsHome.pathString,
                            logPath = environment.logPath.pathString,
                            startupTime = Instant.now().toString(),
                        ),
                    )
                    println(GsonCodec.toJson(ready))
                    System.out.flush()
                }
            }
        } catch (exception: RuntimeException) {
            runtime.log("startup failed: ${exception.message}")
            throw exception
        }
    }

    private fun writeRecord(path: Path, record: DaemonRecord) {
        path.parent.createDirectories()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, GsonCodec.toJson(record))
        Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
