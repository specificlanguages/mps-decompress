import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://artifacts.itemis.cloud/repository/maven-mps")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    "testImplementation"(kotlin("test"))
    "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
