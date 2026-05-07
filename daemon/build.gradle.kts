plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.specificlanguages.mops.daemon.MainKt"
}
