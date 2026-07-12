package com.seapack.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlFilesTest {
    private static final Logger LOGGER = Logger.getLogger(YamlFilesTest.class.getName());

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicSaveCanBeLoadedAgain() throws Exception {
        Path path = temporaryDirectory.resolve("example.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("display-name", "<aqua>Example</aqua>");

        YamlFiles.saveAtomic(path, configuration);

        YamlConfiguration loaded = YamlFiles.load(path, LOGGER, "test config").orElseThrow();
        assertEquals("<aqua>Example</aqua>", loaded.getString("display-name"));
    }

    @Test
    void invalidYamlIsRejectedWithoutChangingTheFile() throws Exception {
        Path path = temporaryDirectory.resolve("broken.yml");
        String invalidYaml = "items:\n  broken: [\n";
        Files.writeString(path, invalidYaml);

        assertTrue(YamlFiles.load(path, LOGGER, "test config").isEmpty());
        assertEquals(invalidYaml, Files.readString(path));
    }
}
