package com.seapack.feature.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.seapack.feature.furniture.FurnitureModelTransform;
import java.io.Reader;
import java.util.Locale;

final class ItemsAdderModelReader {
    private ItemsAdderModelReader() {
    }

    static ItemsAdderModelDefinition read(String modelId, Reader reader) {
        JsonElement parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) {
            throw new JsonParseException("Model root must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        String parentId = null;
        JsonElement parent = root.get("parent");
        if (parent != null && parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()) {
            parentId = normalizeParentModelId(parent.getAsString());
        }

        FurnitureModelTransform transform = null;
        JsonElement displayElement = root.get("display");
        if (displayElement != null && displayElement.isJsonObject()) {
            JsonElement headElement = displayElement.getAsJsonObject().get("head");
            if (headElement != null && headElement.isJsonObject()) {
                JsonObject head = headElement.getAsJsonObject();
                transform = FurnitureModelTransform.discovered(
                        readTransformValues(head.get("rotation")),
                        readTransformValues(head.get("translation"))
                );
            }
        }
        return new ItemsAdderModelDefinition(modelId, parentId, transform);
    }

    private static FurnitureModelTransform.Values readTransformValues(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return FurnitureModelTransform.Values.ZERO;
        }
        JsonArray values = element.getAsJsonArray();
        return new FurnitureModelTransform.Values(
                numberAt(values, 0),
                numberAt(values, 1),
                numberAt(values, 2)
        );
    }

    private static double numberAt(JsonArray values, int index) {
        if (index >= values.size()) {
            return 0.0;
        }
        JsonElement value = values.get(index);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : 0.0;
    }

    private static String normalizeParentModelId(String parent) {
        String normalized = parent.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
