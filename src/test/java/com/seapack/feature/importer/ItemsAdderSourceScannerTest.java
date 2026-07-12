package com.seapack.feature.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemsAdderSourceScannerTest {
    @Test
    void derivesNestedCategoriesFromDirectoryDepth() {
        Path root = Path.of("contents");
        Path config = root.resolve(Path.of(
                "general",
                "configs",
                "furniture",
                "summer",
                "accessories.yml"
        ));

        assertEquals(
                List.of("furniture", "summer"),
                ItemsAdderSourceScanner.categoryFolders(root, config)
        );
    }

    @Test
    void derivesNestedCategoriesFromZipEntries() {
        assertEquals(
                List.of("furniture", "summer"),
                ItemsAdderSourceScanner.categoryFolders(
                        "contents/general/configs/furniture/summer/accessories.yml"
                )
        );
    }

    @Test
    void directConfigFilesStayAtNamespaceRoot() {
        assertEquals(
                List.of(),
                ItemsAdderSourceScanner.categoryFolders("general/configs/items.yml")
        );
    }

    @Test
    void discoversModelIdsInsideImportedResourcePacks() {
        Path root = Path.of("contents");
        Path model = root.resolve(Path.of(
                "general",
                "resourcepack",
                "assets",
                "general",
                "models",
                "furniture",
                "example_model.json"
        ));

        assertEquals("general:furniture/example_model", ItemsAdderSourceScanner.modelId(root, model));
        assertEquals(
                "general:furniture/example_model",
                ItemsAdderSourceScanner.modelId(
                        "contents/general/resourcepack/assets/general/models/furniture/example_model.json"
                )
        );
    }
}
