package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MpsHomeProperty
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.pathString

object MpsJvmArgs {
    fun forMpsHome(mpsHome: Path, ideaConfigDir: Path, ideaSystemDir: Path): List<String> {
        val mpsVersion = mpsVersion(mpsHome)
        return buildList {
            add("-Didea.max.intellisense.filesize=100000")
            add("-D$MpsHomeProperty=${mpsHome.pathString}")
            add("-Didea.config.path=${ideaConfigDir.pathString}")
            add("-Didea.system.path=${ideaSystemDir.pathString}")
            if (mpsVersion != null && mpsVersion >= "2025.2") {
                add("-Didea.platform.prefix=MPS")
            }
            if (mpsVersion != null && mpsVersion >= "2023.3") {
                add("-Dintellij.platform.load.app.info.from.resources=true")
            }
            if (mpsVersion != null && mpsVersion >= "2022.3") {
                add("-Djna.boot.library.path=${jnaPath(mpsHome).pathString}")
            }
            addAll(mpsAddOpens())
        }
    }

    fun requiredJavaMajor(mpsHome: Path): Int {
        val version = mpsVersion(mpsHome)
        return when {
            version == null -> Runtime.version().feature()
            version >= "2025" -> 21
            version >= "2022" -> 17
            else -> 11
        }
    }

    private fun mpsVersion(mpsHome: Path): String? {
        val buildProperties = mpsHome.resolve("build.properties")
        if (!Files.isRegularFile(buildProperties)) {
            return null
        }
        return Files.newInputStream(buildProperties).use { input ->
            Properties().apply { load(input) }.getProperty("mps.build.number")
        }
    }

    private fun jnaPath(mpsHome: Path): Path {
        val base = mpsHome.resolve("lib/jna")
        val platformSpecific = base.resolve(System.getProperty("os.arch"))
        return if (Files.exists(base) && !Files.exists(platformSpecific)) base else platformSpecific
    }

    private fun mpsAddOpens(): List<String> =
        listOf(
            "java.base/java.io",
            "java.base/java.lang",
            "java.base/java.lang.reflect",
            "java.base/java.net",
            "java.base/java.nio",
            "java.base/java.nio.charset",
            "java.base/java.text",
            "java.base/java.time",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/java.util.concurrent.atomic",
            "java.base/jdk.internal.ref",
            "java.base/jdk.internal.vm",
            "java.base/sun.nio.ch",
            "java.base/sun.nio.fs",
            "java.base/sun.security.ssl",
            "java.base/sun.security.util",
            "java.desktop/java.awt",
            "java.desktop/java.awt.dnd.peer",
            "java.desktop/java.awt.event",
            "java.desktop/java.awt.image",
            "java.desktop/java.awt.peer",
            "java.desktop/javax.swing",
            "java.desktop/javax.swing.plaf.basic",
            "java.desktop/javax.swing.text.html",
            "java.desktop/sun.awt.datatransfer",
            "java.desktop/sun.awt.image",
            "java.desktop/sun.awt",
            "java.desktop/sun.font",
            "java.desktop/sun.java2d",
            "java.desktop/sun.swing",
            "jdk.attach/sun.tools.attach",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.internal.jvmstat/sun.jvmstat.monitor",
            "jdk.jdi/com.sun.tools.jdi",
            "java.desktop/sun.lwawt",
            "java.desktop/sun.lwawt.macosx",
            "java.desktop/com.apple.laf",
            "java.desktop/com.apple.eawt",
            "java.desktop/com.apple.eawt.event",
            "java.management/sun.management",
        ).map { "--add-opens=$it=ALL-UNNAMED" }
}
