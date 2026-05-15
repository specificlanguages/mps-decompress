package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Serve loopback daemon requests until stopped or idle."],
)
class MopsDaemonCommand(
    private val jvmCompatibility: (Path) -> MpsJvmCompatibility.Failure? = MpsJvmCompatibility::checkCurrentJvm,
) : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--project-path"], required = true)
    lateinit var projectPath: String

    @Option(names = ["--mps-home"], required = true)
    lateinit var mpsHome: String

    @Option(names = ["--token"], required = true)
    lateinit var token: String

    @Option(names = ["--idea-config-dir"], required = true)
    lateinit var ideaConfigDir: String

    @Option(names = ["--idea-system-dir"], required = true)
    lateinit var ideaSystemDir: String

    @Option(names = ["--log-path"], required = true)
    lateinit var logPath: String

    @Option(names = ["--idle-timeout-ms"])
    var idleTimeoutMillis: Long = Duration.ofMinutes(3).toMillis()

    override fun call(): Int {
        val mpsHomePath = Path.of(mpsHome)
        val logPath = Path.of(logPath)
        val jvmFailure = jvmCompatibility(mpsHomePath)
        if (jvmFailure != null) {
            writeStartupError(jvmFailure, logPath)
            return 1
        }
        val runtime = MpsRuntimeBootstrap(
            projectPath = Path.of(projectPath),
            mpsHome = mpsHomePath,
            ideaConfigDir = Path.of(ideaConfigDir),
            ideaSystemDir = Path.of(ideaSystemDir),
            logPath = logPath,
        )
        try {
            runtime.withLoadedProject { session ->
                PersistentDaemonServer(
                    session = session,
                    expectedToken = token,
                    idleTimeout = Duration.ofMillis(idleTimeoutMillis),
                ).serve { ready ->
                    val environment = session.environment
                    DaemonRecordStore().write(
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
                    spec.commandLine().out.println(GsonCodec.toJson(ready))
                    spec.commandLine().out.flush()
                }
            }
        } catch (exception: RuntimeException) {
            runtime.log("startup failed: ${exception.message}")
            throw exception
        }
        return 0
    }

    private fun writeStartupError(failure: MpsJvmCompatibility.Failure, logPath: Path) {
        logPath.parent.createDirectories()
        val message = "startup failed: ${failure.message}"
        Files.writeString(
            logPath,
            "${Instant.now()} $message\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
        spec.commandLine().out.println(
            GsonCodec.toJson(
                DaemonErrorResponse(
                    type = "error",
                    status = "error",
                    protocolVersion = ProtocolVersion,
                    errorCode = failure.code,
                    message = failure.message,
                    logPath = logPath.pathString,
                ),
            ),
        )
        spec.commandLine().out.flush()
    }
}
