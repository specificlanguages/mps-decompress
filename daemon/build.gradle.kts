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
    applicationName = "mops-daemon"
    mainClass = "com.specificlanguages.mops.daemon.MainKt"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
