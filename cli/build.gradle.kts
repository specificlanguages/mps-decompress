plugins {
    `lifecycle-base`
}

val goExecutable = providers.gradleProperty("goExecutable").orElse("go")

tasks.register<Exec>("goTest") {
    group = "verification"
    description = "Runs the Go CLI test suite."
    workingDir = projectDir
    executable = goExecutable.get()
    args("test", "./...")
}

tasks.register<Exec>("goBuild") {
    group = "build"
    description = "Builds the mops CLI with Go."
    workingDir = projectDir
    executable = goExecutable.get()
    args("build", "./cmd/mops")
}

tasks.register<Delete>("goClean") {
    group = "build"
    description = "Cleans the Go build result."
    delete("mops")
}

tasks.check {
    dependsOn("goTest")
}

tasks.assemble {
    dependsOn("goBuild")
}

tasks.clean {
    dependsOn("goClean")
}