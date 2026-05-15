package com.specificlanguages.mops.launcher;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class MpsLaunchArgs {

    private MpsLaunchArgs() {
    }

    /**
     * @param mpsHome MPS or RCP home. Must exist when this method is being called.
     * @return a list of the JVM arguments required to launch the MPS distribution in MPS home
     */
    public static List<String> getJvmArgsFor(Path mpsHome) {
        MpsVersion mpsVersion = MpsDistributionLayout.readVersion(mpsHome);
        Path jnaLibraryPath = MpsDistributionLayout.findJnaLibrary(mpsHome);

        List<String> result = new ArrayList<>(MPS_ADD_OPENS);

        result.add("-Didea.max.intellisense.filesize=100000");

        if (mpsVersion.isAtLeast(2025, 2)) {
            result.add("-Didea.platform.prefix=MPS");
        }
        if (mpsVersion.isAtLeast(2023, 3)) {
            result.add("-Dintellij.platform.load.app.info.from.resources=true");
        }
        if (mpsVersion.isAtLeast(2022, 3)) {
            result.add("-Djna.boot.library.path=" + jnaLibraryPath);
        }

        return result;
    }

    private static final List<String> MPS_ADD_OPENS = Stream.of(
            "java.base/java.io",
            "java.base/java.lang",
            "java.base/java.lang.reflect",
            "java.base/java.net",
            "java.base/java.nio",
            "java.base/java.nio.charset",
            "java.base/java.text",
            "java.base/java.time",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/java.util.concurrent.atomic",
            "java.base/jdk.internal.ref",
            "java.base/jdk.internal.vm",
            "java.base/sun.nio.ch",
            "java.base/sun.nio.fs",
            "java.base/sun.security.ssl",
            "java.base/sun.security.util",
            "java.desktop/java.awt",
            "java.desktop/java.awt.dnd.peer",
            "java.desktop/java.awt.event",
            "java.desktop/java.awt.image",
            "java.desktop/java.awt.peer",
            "java.desktop/javax.swing",
            "java.desktop/javax.swing.plaf.basic",
            "java.desktop/javax.swing.text.html",
            "java.desktop/sun.awt.datatransfer",
            "java.desktop/sun.awt.image",
            "java.desktop/sun.awt",
            "java.desktop/sun.font",
            "java.desktop/sun.java2d",
            "java.desktop/sun.swing",
            "jdk.attach/sun.tools.attach",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.internal.jvmstat/sun.jvmstat.monitor",
            "jdk.jdi/com.sun.tools.jdi",
            "java.desktop/sun.lwawt",
            "java.desktop/sun.lwawt.macosx",
            "java.desktop/com.apple.laf",
            "java.desktop/com.apple.eawt",
            "java.desktop/com.apple.eawt.event",
            "java.management/sun.management"
    ).map(module -> "--add-opens=" + module + "=ALL-UNNAMED").toList();
}
