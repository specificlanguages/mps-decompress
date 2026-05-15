package com.specificlanguages.mops.launcher;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Utilities to find information in MPS/RCP distributions.
 */
public final class MpsDistributionLayout {

    private MpsDistributionLayout() {
    }

    public static @Nullable Path findBundledJavaHome(Path mpsHome) {
        Path javaHome;

        String osName = System.getProperty("os.name");
        if (osName.startsWith("Mac")) {
            javaHome = mpsHome.resolve("jbr/Contents/Home");
        } else {
            javaHome = mpsHome.resolve("jbr");
        }

        if (!Files.isDirectory(javaHome)) {
            return null;
        }

        return javaHome;
    }

    public static Path findJnaLibrary(Path mpsHome) {
        return findJnaLibrary(mpsHome, System.getProperty("os.arch"));
    }

    public static Path findJnaLibrary(Path mpsHome, String osArch) {
        Path base = mpsHome.resolve("lib/jna");
        Path platformSpecific = base.resolve(osArch);

        if (Files.exists(platformSpecific)) {
            return platformSpecific;
        }

        return base;
    }

    public static MpsVersion readVersion(Path mpsHome) {
        Path buildProperties = mpsHome.resolve("build.properties");
        if (!Files.isRegularFile(buildProperties)) {
            throw new IllegalStateException("MPS home directory does not contain build.properties file: " + mpsHome);
        }

        String rawBuildNumber;

        {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(buildProperties)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Error loading properties from file " + buildProperties, exception);
            }

            rawBuildNumber = properties.getProperty("mps.build.number");
        }

        if (rawBuildNumber == null || rawBuildNumber.isBlank()) {
            throw new IllegalStateException("MPS build number is missing or empty in build.properties file: " + buildProperties);
        }

        String buildNumber = rawBuildNumber.replaceFirst("^\\p{Alpha}+-", "");

        if (buildNumber.isEmpty() || !Character.isDigit(buildNumber.charAt(0))) {
            throw new IllegalArgumentException("build number must start with a digit: " + buildNumber);
        }

        try {
            String[] components = buildNumber.split("\\.", 3);

            if (components[0].length() == 3) {
                return new MpsVersion(
                        2000 + Integer.parseInt(components[0].substring(0, 2)),
                        Integer.parseInt(components[0].substring(2, 3)));
            }

            if (components.length < 2) {
                throw new IllegalArgumentException("invalid build number: " + rawBuildNumber);
            }

            return new MpsVersion(Integer.parseInt(components[0]), Integer.parseInt(components[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid build number: " + rawBuildNumber, e);
        }
    }
}
