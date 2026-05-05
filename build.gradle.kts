plugins {
    base
}

group = "com.specificlanguages.mops"
version = "0.2.0"

subprojects {
    apply(plugin = "base")
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}
