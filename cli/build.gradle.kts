plugins {
    id("mops.kotlin-jvm-conventions")
    application
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":protocol"))
    implementation("info.picocli:picocli:4.7.7")
}

application {
    applicationName = "mops"
    mainClass = "com.specificlanguages.mops.cli.MainKt"
}

tasks.named<JavaExec>("run") {
    dependsOn(":daemon:installDist")
    environment(
        "MOPS_DAEMON_CLASSPATH",
        fileTree(rootProject.layout.projectDirectory.dir("daemon/build/install/mops-daemon/lib")) {
            include("*.jar")
        }.asPath,
    )
}
