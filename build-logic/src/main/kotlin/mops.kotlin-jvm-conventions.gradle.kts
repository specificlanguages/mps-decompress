import org.gradle.api.tasks.testing.Test

plugins {
    id("mops.jvm-conventions")
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
}
