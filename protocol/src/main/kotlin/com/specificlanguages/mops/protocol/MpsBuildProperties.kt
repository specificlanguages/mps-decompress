package com.specificlanguages.mops.protocol

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object MpsBuildProperties {
    data class Version(
        val major: Int,
        val minor: Int,
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, Version::major, Version::minor)

        fun isAtLeast(major: Int, minor: Int): Boolean =
            this.major > major || this.major == major && this.minor >= minor
    }

    fun buildNumber(mpsHome: Path): String? {
        val buildProperties = mpsHome.resolve("build.properties")
        if (!Files.isRegularFile(buildProperties)) {
            return null
        }
        return Files.newInputStream(buildProperties).use { input ->
            Properties().apply { load(input) }.getProperty("mps.build.number")
        }
    }

    fun version(mpsHome: Path): Version? =
        buildNumber(mpsHome)?.let(::version)

    fun version(buildNumber: String): Version? {
        val parts = buildNumber.split('.', limit = 3)
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return Version(major, minor)
    }
}
