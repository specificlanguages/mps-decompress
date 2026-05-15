plugins {
    id("mops.kotlin-jvm-conventions")
    application
}

val mpsZip: Configuration by configurations.creating
val mpsRuntime: Configuration by configurations.creating

configurations {
    compileOnly.get().extendsFrom(mpsRuntime)
    testCompileOnly.get().extendsFrom(mpsRuntime)
}

dependencies {
    implementation(project(":protocol"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("de.itemis.mps.build-backends:project-loader:5.0.1.180.8e0fd7e")

    mpsRuntime(zipTree({ mpsZip.singleFile }).matching {
        include("lib/mps-core.jar")
        include("lib/mps-environment.jar")
        include("lib/mps-platform.jar")
        include("lib/mps-openapi.jar")
        include("lib/mps-logging.jar")
        include("lib/platform-api.jar")
        include("lib/util.jar")
        include("lib/util-8.jar")
        include("lib/testFramework.jar")
        include("lib/app.jar")
    })
    mpsZip("com.jetbrains:mps:2024.3.1")
    testRuntimeOnly(zipTree({ mpsZip.singleFile }).matching {
        include("lib/mps-openapi.jar")
    })
}

application {
    applicationName = "mops-daemon"
    mainClass = "com.specificlanguages.mops.daemon.MainKt"
}
