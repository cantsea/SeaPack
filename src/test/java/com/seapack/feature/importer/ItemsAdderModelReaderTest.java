package com.seapack.feature.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

class ItemsAdderModelReaderTest {
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

        ItemsAdderModelDefinition model = ItemsAdderModelReader.read(
                "general:furniture/example_model",
                new StringReader(json)
        );

        assertEquals(180.0, model.transform().rotation().y());
        assertEquals(-29.75, model.transform().translation().y());
        assertEquals(7.0, model.transform().translation().z());
    }
}
