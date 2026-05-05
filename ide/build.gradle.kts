val mpsModulesFile = layout.projectDirectory.file(".mps/modules.xml")

tasks.register("checkMpsProjectLayout") {
    group = "verification"
    description = "Checks that ide/ is an MPS project home."
    inputs.file(mpsModulesFile)

    doLast {
        val modulesFile = mpsModulesFile.asFile
        require(modulesFile.isFile) {
            "Expected MPS project modules file at ${modulesFile.relativeTo(projectDir)}"
        }
        require("<component name=\"MPSProject\">" in modulesFile.readText()) {
            "Expected ${modulesFile.relativeTo(projectDir)} to declare an MPSProject component"
        }
    }
}

tasks.named("check") {
    dependsOn("checkMpsProjectLayout")
}
