package org.seapack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemsAdderMetadataLoaderTest {
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
                ItemsAdderMetadataLoader.categoryFolders(root, config)
        );
    }

    @Test
    void derivesNestedCategoriesFromZipEntries() {
        assertEquals(
                List.of("furniture", "summer"),
                ItemsAdderMetadataLoader.categoryFolders(
                        "contents/general/configs/furniture/summer/accessories.yml"
                )
        );
    }

    @Test
    void directConfigFilesStayAtNamespaceRoot() {
        assertEquals(
                List.of(),
                ItemsAdderMetadataLoader.categoryFolders("general/configs/items.yml")
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

        assertEquals("general:furniture/example_model", ItemsAdderMetadataLoader.modelId(root, model));
        assertEquals(
                "general:furniture/example_model",
                ItemsAdderMetadataLoader.modelId(
                        "contents/general/resourcepack/assets/general/models/furniture/example_model.json"
                )
        );
    }

    @Test
    void readsEditableHeadTransformDefaultsFromTheFurnitureModel() {
        String json = """
                {
                  "display": {
                    "head": {
                      "rotation": [0, 180, 0],
                      "translation": [0, -29.75, 7]
                    }
                  }
                }
                """;

        ItemsAdderMetadataLoader.ModelDefinition model = ItemsAdderMetadataLoader.readModelDefinition(
                "general:furniture/example_model",
                new StringReader(json)
        );

        assertEquals(180.0, model.transform().rotation().y());
        assertEquals(-29.75, model.transform().translation().y());
        assertEquals(7.0, model.transform().translation().z());
    }

}
