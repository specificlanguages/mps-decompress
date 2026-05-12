package com.specificlanguages.mops.daemon

import kotlin.system.exitProcess
import picocli.CommandLine

fun main(args: Array<String>) {
    exitProcess(CommandLine(MopsDaemonCommand()).execute(*args))
}
