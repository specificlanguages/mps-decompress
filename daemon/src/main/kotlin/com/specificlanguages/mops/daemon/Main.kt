package com.specificlanguages.mops.daemon

import com.google.gson.Gson
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.Plugin
import de.itemis.mps.gradle.project.loader.ProjectLoader
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.system.exitProcess
import org.w3c.dom.Document
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

private const val ProtocolVersion = 1
private const val MpsHomeProperty = "mops.mps.home"
private val GsonCodec = Gson()

fun main(args: Array<String>) {
    exitProcess(CommandLine(MopsDaemonCommand()).execute(*args))
}

@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Daemon process skeleton for MPS-backed mops operations."],
    subcommands = [
        SingleUsePingCommand::class,
        ServeCommand::class,
    ],
)
class MopsDaemonCommand : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(name = "serve", description = ["Serve loopback daemon requests until stopped or idle."])
class ServeCommand : Runnable {
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

private fun resolveMpsHomeFromRuntime(): Path =
    System.getProperty(MpsHomeProperty)
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }
        ?: System.getenv("MOPS_MPS_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
        ?: throw IllegalStateException(
            "MPS home is not configured for the daemon JVM; expected -D$MpsHomeProperty=<path>",
        )

class SingleUsePingServer(
    private val environment: MpsEnvironmentState,
    private val expectedToken: String,
    private val protocolVersion: Int = ProtocolVersion,
) {
    fun serveOnce(onReady: (ReadyMessage) -> Unit = {}) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            onReady(
                ReadyMessage(
                    type = "ready",
                    protocolVersion = protocolVersion,
                    port = server.localPort,
                ),
            )

            server.accept().use { socket ->
                val request = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                val response = handle(request)
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    writer.println(GsonCodec.toJson(response))
                }
            }
        }
    }

    fun handle(requestLine: String?): PingResponse {
        val request = try {
            GsonCodec.fromJson(requestLine, PingRequest::class.java)
        } catch (_: RuntimeException) {
            return error("INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }

        if (request == null || request.type != "ping") {
            return error("INVALID_REQUEST", "request type must be ping")
        }
        if (request.protocolVersion != protocolVersion) {
            return error("PROTOCOL_MISMATCH", "unsupported protocol version ${request.protocolVersion}")
        }
        if (request.token != expectedToken) {
            return error("TOKEN_MISMATCH", "invalid daemon token")
        }

        return PingResponse(
            type = "ping",
            status = "ok",
            protocolVersion = protocolVersion,
            projectPath = environment.projectPath.pathString,
            mpsHome = environment.mpsHome.pathString,
            environmentReady = true,
            logPath = environment.logPath.pathString,
            ideaConfigPath = environment.ideaConfigDir.pathString,
            ideaSystemPath = environment.ideaSystemDir.pathString,
        )
    }

    private fun error(code: String, message: String): PingResponse =
        PingResponse(
            type = "ping",
            status = "error",
            protocolVersion = protocolVersion,
            projectPath = environment.projectPath.pathString,
            mpsHome = environment.mpsHome.pathString,
            environmentReady = true,
            logPath = environment.logPath.pathString,
            ideaConfigPath = environment.ideaConfigDir.pathString,
            ideaSystemPath = environment.ideaSystemDir.pathString,
            errorCode = code,
            message = message,
        )
}

class PersistentDaemonServer(
    private val environment: MpsEnvironmentState,
    private val expectedToken: String,
    private val protocolVersion: Int = ProtocolVersion,
    private val idleTimeout: Duration = Duration.ofMinutes(3),
) {
    fun serve(onReady: (ReadyMessage) -> Unit = {}) {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = idleTimeout.toMillis().toInt()
            onReady(
                ReadyMessage(
                    type = "ready",
                    protocolVersion = protocolVersion,
                    port = server.localPort,
                ),
            )

            var stopping = false
            while (!stopping) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    break
                }
                socket.use {
                    val request = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                    val response = handle(request)
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(GsonCodec.toJson(response))
                    }
                    if (response.type == "stop" && response.status == "ok") {
                        stopping = true
                    }
                }
            }
        }
    }

    fun handle(requestLine: String?): PingResponse {
        val request = try {
            GsonCodec.fromJson(requestLine, DaemonRequest::class.java)
        } catch (_: RuntimeException) {
            return error("ping", "INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }

        if (request == null) {
            return error("ping", "INVALID_REQUEST", "request must be one newline-delimited JSON object")
        }
        if (request.protocolVersion != protocolVersion) {
            return error(request.type, "PROTOCOL_MISMATCH", "unsupported protocol version ${request.protocolVersion}")
        }
        if (request.token != expectedToken) {
            return error(request.type, "TOKEN_MISMATCH", "invalid daemon token")
        }

        return when (request.type) {
            "ping" -> PingResponse(
                type = "ping",
                status = "ok",
                protocolVersion = protocolVersion,
                projectPath = environment.projectPath.pathString,
                mpsHome = environment.mpsHome.pathString,
                environmentReady = true,
                logPath = environment.logPath.pathString,
                ideaConfigPath = environment.ideaConfigDir.pathString,
                ideaSystemPath = environment.ideaSystemDir.pathString,
            )
            "stop" -> PingResponse(
                type = "stop",
                status = "ok",
                protocolVersion = protocolVersion,
                projectPath = environment.projectPath.pathString,
                mpsHome = environment.mpsHome.pathString,
                environmentReady = true,
                logPath = environment.logPath.pathString,
                ideaConfigPath = environment.ideaConfigDir.pathString,
                ideaSystemPath = environment.ideaSystemDir.pathString,
            )
            "model-resave" -> PingResponse(
                type = "model-resave",
                status = "error",
                protocolVersion = protocolVersion,
                projectPath = environment.projectPath.pathString,
                mpsHome = environment.mpsHome.pathString,
                environmentReady = true,
                logPath = environment.logPath.pathString,
                ideaConfigPath = environment.ideaConfigDir.pathString,
                ideaSystemPath = environment.ideaSystemDir.pathString,
                modelTarget = request.modelTarget,
                errorCode = "NOT_IMPLEMENTED",
                message = "model resave is routed through the MPS daemon, but the MPS API resave implementation is not wired yet",
            )
            else -> error(request.type, "INVALID_REQUEST", "unsupported request type ${request.type}")
        }
    }

    private fun error(type: String, code: String, message: String): PingResponse =
        PingResponse(
            type = type,
            status = "error",
            protocolVersion = protocolVersion,
            projectPath = environment.projectPath.pathString,
            mpsHome = environment.mpsHome.pathString,
            environmentReady = true,
            logPath = environment.logPath.pathString,
            ideaConfigPath = environment.ideaConfigDir.pathString,
            ideaSystemPath = environment.ideaSystemDir.pathString,
            errorCode = code,
            message = message,
        )
}

class MpsRuntimeBootstrap(
    private val projectPath: Path,
    private val mpsHome: Path,
    private val ideaConfigDir: Path,
    private val ideaSystemDir: Path,
    private val logPath: Path,
    private val projectSessionOpener: MpsProjectSessionOpener = ProjectLoaderMpsProjectSessionOpener(),
) {
    fun initialize(): MpsEnvironmentState {
        logPath.parent.createDirectories()
        log("initializing MPS daemon runtime")
        requireDirectory(projectPath, "project path")
        requireDirectory(projectPath.resolve(".mps"), "MPS project marker")
        requireDirectory(mpsHome, "MPS home")
        requireFile(mpsHome.resolve("build.properties"), "MPS build properties")
        requireDirectory(mpsHome.resolve("plugins"), "MPS plugins directory")
        ideaConfigDir.createDirectories()
        ideaSystemDir.createDirectories()
        log("idea.config.path=${ideaConfigDir.pathString}")
        log("idea.system.path=${ideaSystemDir.pathString}")

        return MpsEnvironmentState(
            projectPath = projectPath,
            mpsHome = mpsHome,
            ideaConfigDir = ideaConfigDir,
            ideaSystemDir = ideaSystemDir,
            logPath = logPath,
        )
    }

    fun <T> withLoadedProject(action: (MpsEnvironmentState) -> T): T {
        val environment = initialize()
        val pluginRoot = mpsHome.resolve("plugins")
        val plugins = PluginScanner.findPlugins(pluginRoot)
        log("opening IDEA environment for project ${projectPath.pathString} with ${plugins.size} plugins from ${pluginRoot.pathString}")
        return projectSessionOpener.withOpenProject(
            MpsProjectSessionConfig(
                projectPath = projectPath,
                mpsHome = mpsHome,
                pluginRoot = pluginRoot,
                plugins = plugins,
                buildNumber = mpsBuildNumber(mpsHome),
            ),
        ) {
            log("environment ready for project ${projectPath.pathString}")
            action(environment)
        }
    }

    fun log(message: String) {
        logPath.parent.createDirectories()
        Files.writeString(
            logPath,
            "${Instant.now()} $message\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private fun requireDirectory(path: Path, label: String) {
        if (!Files.isDirectory(path)) {
            throw IllegalStateException("$label is not a directory: ${path.pathString}")
        }
    }

    private fun requireFile(path: Path, label: String) {
        if (!Files.isRegularFile(path)) {
            throw IllegalStateException("$label is missing: ${path.pathString}")
        }
    }

    private fun mpsBuildNumber(mpsHome: Path): String? =
        Files.newInputStream(mpsHome.resolve("build.properties")).use { input ->
            Properties().apply { load(input) }.getProperty("mps.build.number")
        }
}

interface MpsProjectSessionOpener {
    fun <T> withOpenProject(config: MpsProjectSessionConfig, action: () -> T): T
}

class ProjectLoaderMpsProjectSessionOpener : MpsProjectSessionOpener {
    override fun <T> withOpenProject(config: MpsProjectSessionConfig, action: () -> T): T {
        val loader = ProjectLoader.build {
            environmentKind = EnvironmentKind.IDEA
            buildNumber = config.buildNumber
            environmentConfig {
                pluginLocation = config.pluginRoot.toFile()
                plugins.addAll(config.plugins.map { Plugin(it.id, it.path.pathString) })
            }
        }
        return loader.executeWithProject(config.projectPath.toFile()) { _, _ -> action() }
    }
}

data class MpsProjectSessionConfig(
    val projectPath: Path,
    val mpsHome: Path,
    val pluginRoot: Path,
    val plugins: List<DetectedPlugin>,
    val buildNumber: String?,
)

data class DetectedPlugin(
    val id: String,
    val path: Path,
)

object PluginScanner {
    fun findPlugins(root: Path): List<DetectedPlugin> {
        if (!root.isDirectory()) {
            return emptyList()
        }
        val plugins = mutableListOf<DetectedPlugin>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val id = readPluginId(dir.toFile())
                if (id != null) {
                    plugins.add(DetectedPlugin(id, dir.toAbsolutePath().normalize()))
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        })
        return plugins
    }

    private fun readPluginId(pluginDirectory: File): String? {
        val pluginXml = findPluginDescriptor(pluginDirectory) ?: return null
        val ids = pluginXml.documentElement.getElementsByTagName("id")
        if (ids.length != 1) {
            return null
        }
        return ids.item(0).textContent.takeIf { it.isNotBlank() }
    }

    private fun findPluginDescriptor(pluginDirectory: File): Document? {
        val libDir = pluginDirectory.resolve("lib")
        if (libDir.isDirectory) {
            libDir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }
                ?.forEach { jar ->
                    readDescriptorFromJarFile(jar)?.let { return it }
                }
        }

        val pluginXmlFile = pluginDirectory.resolve("META-INF/plugin.xml")
        return if (pluginXmlFile.isFile) {
            readXmlFile(pluginXmlFile)
        } else {
            null
        }
    }

    private fun readDescriptorFromJarFile(file: File): Document? =
        try {
            JarFile(file).use { jarFile ->
                val jarEntry = jarFile.getJarEntry("META-INF/plugin.xml") ?: return null
                jarFile.getInputStream(jarEntry).use {
                    readXmlFile(it, "${file}!${jarEntry.name}")
                }
            }
        } catch (_: Exception) {
            null
        }

    private fun readXmlFile(file: File): Document? =
        try {
            newDocumentBuilder().parse(file)
        } catch (_: Exception) {
            null
        }

    private fun readXmlFile(stream: InputStream, name: String): Document? =
        try {
            newDocumentBuilder().parse(stream, name)
        } catch (_: Exception) {
            null
        }

    private fun newDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            isNamespaceAware = true
            setFeature("http://xml.org/sax/features/namespaces", false)
            setFeature("http://xml.org/sax/features/validation", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder()
}

data class MpsEnvironmentState(
    val projectPath: Path,
    val mpsHome: Path,
    val ideaConfigDir: Path,
    val ideaSystemDir: Path,
    val logPath: Path,
)

data class ReadyMessage(
    val type: String,
    val protocolVersion: Int,
    val port: Int,
)

data class PingRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
)

data class DaemonRequest(
    val type: String,
    val protocolVersion: Int,
    val token: String,
    val modelTarget: String? = null,
)

data class DaemonRecord(
    val port: Int,
    val token: String,
    val pid: Long,
    val protocolVersion: Int,
    val daemonVersion: String,
    val projectPath: String,
    val mpsHome: String,
    val logPath: String,
    val startupTime: String,
)

data class PingResponse(
    val type: String,
    val status: String,
    val protocolVersion: Int,
    val projectPath: String,
    val mpsHome: String,
    val environmentReady: Boolean = false,
    val logPath: String? = null,
    val ideaConfigPath: String? = null,
    val ideaSystemPath: String? = null,
    val modelTarget: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
)
