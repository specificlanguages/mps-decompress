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

tasks.named("check") {
    dependsOn("goTest")
}

tasks.named("assemble") {
    dependsOn("goBuild")
}
