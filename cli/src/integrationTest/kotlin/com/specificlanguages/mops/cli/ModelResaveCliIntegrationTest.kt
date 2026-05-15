package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

class ModelResaveCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `model resave command restores resolve attributes through daemon`() {
        val project = copyTestProject("mps-json")
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val original = model.readText()
        assertTrue(original.contains(""" resolve=""""), "fixture should contain resolve attributes")
        model.writeText(original.replace(Regex(""" resolve="[^"]*"""")) { "" })
        assertFalse(model.readText().contains(""" resolve=""""), "test setup should remove resolve attributes")

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val environment = mapOf(
            "MOPS_DAEMON_CLASSPATH" to requiredProperty("mops.integration.daemonClasspath"),
            "MOPS_DAEMON_HOME" to daemonHome.pathString,
        )
        val launcher = ProcessDaemonLauncher(
            environment = environment,
            timeout = Duration.ofMinutes(2),
        )
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            val exitCode = newCommandLine(
                launcher = launcher,
                environment = environment,
                workingDirectory = project,
            ).also {
                it.out = PrintWriter(stdout, true)
                it.err = PrintWriter(stderr, true)
            }.execute(
                "--java-home",
                requiredProperty("mops.integration.javaHome"),
                "--mps-home",
                requiredProperty("mops.integration.mpsHome"),
                "model",
                "resave",
                model.pathString,
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            assertContains(stdout.toString(), "resaved ${model.toRealPath().normalize()}")
            assertEquals(original, model.readText())
        } finally {
            newCommandLine(
                launcher = launcher,
                environment = environment,
                workingDirectory = project,
            ).execute("daemon", "stop")
        }
    }

    private fun copyTestProject(name: String): Path {
        val source = Path.of(
            requireNotNull(javaClass.classLoader.getResource("test-projects/$name")) {
                "missing test project resource test-projects/$name"
            }.toURI(),
        )
        val target = tempDir.resolve(name).createDirectories()
        copyDirectory(source, target)
        return target
    }

    private fun copyDirectory(source: Path, target: Path) {
        target.createDirectories()
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).pathString)
                if (Files.isDirectory(path)) {
                    destination.createDirectories()
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun requiredProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "missing system property $name" }
}
