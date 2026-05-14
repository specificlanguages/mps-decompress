package com.specificlanguages.mops.cli

import java.nio.file.Files
import java.nio.file.Path

object DaemonJavaHome {
    fun executable(
        explicitJavaHome: Path?,
        mpsHome: Path,
        currentJavaHome: Path = Path.of(System.getProperty("java.home")),
        osName: String = System.getProperty("os.name"),
    ): Path {
        val javaHome = explicitJavaHome
            ?: mpsHome.resolve("jbr").takeIf { Files.isDirectory(it) }
            ?: currentJavaHome
        return executableIn(javaHome, osName)
    }

    fun executableIn(javaHome: Path, osName: String = System.getProperty("os.name")): Path {
        val executableName = if (osName.startsWith("Windows")) "java.exe" else "java"
        val macOsBundleExecutable = javaHome.resolve("Contents/Home/bin").resolve(executableName)
        return if (osName.startsWith("Mac") && Files.isRegularFile(macOsBundleExecutable)) {
            macOsBundleExecutable
        } else {
            javaHome.resolve("bin").resolve(executableName)
        }
    }
}
