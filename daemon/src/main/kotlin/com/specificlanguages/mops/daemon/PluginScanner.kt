package com.specificlanguages.mops.daemon

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import org.w3c.dom.Document

/**
 * Finds classic IntelliJ/MPS plugin descriptors under an MPS `plugins` directory.
 *
 * The scanner is deliberately tolerant: unreadable descriptors and plugins without a single explicit `<id>` are skipped
 * instead of failing daemon startup.
 */
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
