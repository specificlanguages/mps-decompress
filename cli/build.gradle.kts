plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "mops"
    mainClass = "com.specificlanguages.mops.cli.MainKt"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
