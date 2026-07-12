package org.seapack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemsAdderImporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalPluralCacheWinsEvenWhenSingularIsNewer() throws Exception {
        Path plural = Files.writeString(temporaryDirectory.resolve("items_ids_cache.yml"), "PAPER: {}\n");
        Path singular = Files.writeString(temporaryDirectory.resolve("items_id_cache.yml"), "PAPER: {}\n");
        Files.setLastModifiedTime(plural, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(singular, FileTime.fromMillis(2_000));

        assertEquals(plural, ItemsAdderImporter.findCacheFile(temporaryDirectory));
    }

    @Test
    void singularCacheIsUsedOnlyWhenCanonicalFileIsAbsent() throws Exception {
        Path singular = Files.writeString(temporaryDirectory.resolve("items_id_cache.yml"), "PAPER: {}\n");

        assertEquals(singular, ItemsAdderImporter.findCacheFile(temporaryDirectory));
    }
}
