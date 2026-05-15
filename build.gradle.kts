plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
}

group = "com.specificlanguages.mops"
version = "0.3.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}
