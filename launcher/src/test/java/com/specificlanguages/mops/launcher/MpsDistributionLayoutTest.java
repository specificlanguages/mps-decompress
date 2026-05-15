package com.specificlanguages.mops.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpsDistributionLayoutTest {

    @Test
    public void readsVersionFromBuildProperties(@TempDir Path tempDir) throws IOException {
        Path buildProperties = tempDir.resolve("build.properties");
        Files.writeString(buildProperties, "mps.build.number=MPS-213.7172.1079\n", StandardCharsets.ISO_8859_1);
        assertEquals(new MpsVersion(2021, 3), MpsDistributionLayout.readVersion(tempDir));
    }

}
