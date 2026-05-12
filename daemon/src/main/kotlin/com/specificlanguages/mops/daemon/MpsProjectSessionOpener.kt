package com.specificlanguages.mops.daemon

interface MpsProjectSessionOpener {
    fun <T> withOpenProject(config: MpsProjectSessionConfig, action: () -> T): T
}
