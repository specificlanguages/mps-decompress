package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsBuildProperties
import java.nio.file.Path

object MpsJvmCompatibility {
    data class Failure(
        val code: String,
        val message: String,
    )

    data class JavaRuntime(
        val major: Int,
        val vendor: String,
        val vmVendor: String,
        val runtimeName: String,
        val vmName: String,
    ) {
        val isJetBrains: Boolean =
            listOf(vendor, vmVendor, runtimeName, vmName).any { it.contains("JetBrains", ignoreCase = true) }
    }

    fun checkCurrentJvm(mpsHome: Path): Failure? =
        check(mpsHome, currentRuntime())

    fun check(mpsHome: Path, runtime: JavaRuntime): Failure? {
        val mpsBuildNumber = MpsBuildProperties.buildNumber(mpsHome) ?: return null
        val requiredJavaMajor = requiredJavaMajor(mpsBuildNumber)
        if (runtime.major != requiredJavaMajor) {
            return Failure(
                code = "JVM_VERSION_MISMATCH",
                message = "daemon JVM ${runtime.major} is not compatible with MPS $mpsBuildNumber; required Java $requiredJavaMajor",
            )
        }
        if (!runtime.isJetBrains) {
            return Failure(
                code = "JVM_VENDOR_UNSUPPORTED",
                message = "daemon JVM is not a JetBrains Runtime; current vendor is '${runtime.vendor}'",
            )
        }
        return null
    }

    private fun currentRuntime(): JavaRuntime =
        JavaRuntime(
            major = Runtime.version().feature(),
            vendor = System.getProperty("java.vendor", ""),
            vmVendor = System.getProperty("java.vm.vendor", ""),
            runtimeName = System.getProperty("java.runtime.name", ""),
            vmName = System.getProperty("java.vm.name", ""),
        )

    private fun requiredJavaMajor(mpsBuildNumber: String): Int {
        val mpsMajor = MpsBuildProperties.version(mpsBuildNumber)?.major
            ?: return Runtime.version().feature()
        return when {
            mpsMajor >= 2025 -> 21
            mpsMajor >= 2022 -> 17
            else -> 11
        }
    }
}
