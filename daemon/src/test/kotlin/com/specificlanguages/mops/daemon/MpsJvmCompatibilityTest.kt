package com.specificlanguages.mops.daemon

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class MpsJvmCompatibilityTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `MPS 2025 requires Java 21`() {
        val mpsHome = mpsHomeWithBuildNumber("2025.1")

        val failure = MpsJvmCompatibility.check(mpsHome, jetBrainsRuntime(17))

        assertEquals("JVM_VERSION_MISMATCH", failure?.code)
        assertEquals(true, failure?.message?.contains("required Java 21"))
    }

    @Test
    fun `daemon JVM must be JetBrains Runtime`() {
        val mpsHome = mpsHomeWithBuildNumber("2024.3")

        val failure = MpsJvmCompatibility.check(
            mpsHome,
            MpsJvmCompatibility.JavaRuntime(
                major = 17,
                vendor = "Eclipse Adoptium",
                vmVendor = "Eclipse Adoptium",
                runtimeName = "OpenJDK Runtime Environment",
                vmName = "OpenJDK 64-Bit Server VM",
            ),
        )

        assertEquals("JVM_VENDOR_UNSUPPORTED", failure?.code)
    }

    @Test
    fun `matching JetBrains Runtime is accepted`() {
        val mpsHome = mpsHomeWithBuildNumber("2024.3")

        assertNull(MpsJvmCompatibility.check(mpsHome, jetBrainsRuntime(17)))
    }

    private fun mpsHomeWithBuildNumber(buildNumber: String): Path {
        val mpsHome = tempDir.resolve("mps-$buildNumber").createDirectories()
        java.nio.file.Files.writeString(mpsHome.resolve("build.properties"), "mps.build.number=$buildNumber\n")
        return mpsHome
    }

    private fun jetBrainsRuntime(major: Int): MpsJvmCompatibility.JavaRuntime =
        MpsJvmCompatibility.JavaRuntime(
            major = major,
            vendor = "JetBrains s.r.o.",
            vmVendor = "JetBrains s.r.o.",
            runtimeName = "OpenJDK Runtime Environment",
            vmName = "OpenJDK 64-Bit Server VM",
        )
}
