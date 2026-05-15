import com.specificlanguages.jbrtoolchain.JbrToolchainExtension
import com.specificlanguages.mpsplatformcache.MpsPlatformCache

plugins {
    id("mops.kotlin-jvm-conventions")
    application

    id("com.specificlanguages.mps-platform-cache") version "1.0.0"
    id("com.specificlanguages.jbr-toolchain") version "1.0.2"
}

val integrationTestMps: Configuration by configurations.creating {
    isCanBeConsumed = false
}

val integrationTest by sourceSets.creating {
    resources.srcDir(project(":daemon").projectDir.resolve("src/test/resources"))
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val daemonDist by configurations.registering {
    isCanBeResolved = false
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":protocol"))
    implementation("info.picocli:picocli:4.7.7")

    integrationTestMps("com.jetbrains:mps:2025.1.2")
    add("jbr", "com.jetbrains.jdk:jbr_jcef:21.0.8-b895.146")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    daemonDist(project(path = ":daemon", configuration = "dist"))
}

configurations.named(integrationTest.implementationConfigurationName) {
    extendsFrom(
        configurations.implementation.get(),
        configurations.testImplementation.get(),
    )
}

configurations.named(integrationTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.runtimeOnly.get())
}

application {
    applicationName = "mops"
    mainClass = "com.specificlanguages.mops.cli.MainKt"
}

val integrationTestMpsRoot = mpsPlatformCache.getMpsRoot(providers.provider { integrationTestMps })
val integrationTestJbr = jbrToolchain.javaLauncher
val integrationTestDaemonClasspath = providers.provider {
    fileTree(project(":daemon").layout.buildDirectory.dir("install/mops-daemon/lib")) {
        include("*.jar")
    }.asPath
}

tasks.register<Test>("integrationTest") {
    description = "Runs CLI integration tests against a daemon started with downloaded MPS and JBR distributions."
    dependsOn(":daemon:installDist")

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    doFirst {
        systemProperty("mops.integration.mpsHome", integrationTestMpsRoot.get().absolutePath)
        systemProperty(
            "mops.integration.javaHome",
            integrationTestJbr.get().metadata.installationPath.asFile.absolutePath,
        )
        systemProperty("mops.integration.daemonClasspath", integrationTestDaemonClasspath.get())
    }
}

tasks.named("check") {
    dependsOn("integrationTest")
}

tasks.named<JavaExec>("run") {
    dependsOn(daemonDist)
    environment(
        "MOPS_DAEMON_CLASSPATH",
        fileTree(daemonDist.map { it.asFileTree.matching { include("lib/*.jar") } }).asPath,
    )
}
