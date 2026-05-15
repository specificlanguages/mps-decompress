package com.specificlanguages.mops.daemon

import java.nio.file.Path
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.project.Project

internal fun testMpsProjectSession(projectPath: Path = Path.of("/project")): MpsProjectSession =
    MpsProjectSession(
        environment = MpsEnvironmentState(
            projectPath = projectPath,
            mpsHome = Path.of("/mps"),
            ideaConfigDir = Path.of("/state/config"),
            ideaSystemDir = Path.of("/state/system"),
            logPath = Path.of("/state/daemon.log"),
        ),
        project = FakeMpsProject,
    )

internal object FakeMpsProject : Project {
    override fun getRepository(): SRepository =
        error("FakeMpsProject repository is not used by these tests")

    override fun getModelAccess(): ModelAccess =
        error("FakeMpsProject model access is not used by these tests")

    override fun getName(): String = "fake-project"

    override fun getProjectModules(): MutableList<SModule> = mutableListOf()

    override fun isOpened(): Boolean = true
}
