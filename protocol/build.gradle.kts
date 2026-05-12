plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(17)
}
