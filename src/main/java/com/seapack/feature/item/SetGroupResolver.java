package com.seapack.feature.item;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SetGroupResolver {
    private SetGroupResolver() {
    }

    static Map<String, String> aliases(List<CustomItemDefinition> items) {
        Set<String> rawGroups = new HashSet<>();
        for (CustomItemDefinition item : items) {
            if (!item.category().equalsIgnoreCase("sets")) {
                continue;
            }
            String rawGroup = item.setGroup();
            if (!rawGroup.isBlank()) {
                rawGroups.add(rawGroup);
            }
        }

        return rawGroups.stream().collect(Collectors.toUnmodifiableMap(
                Function.identity(),
                rawGroup -> rawGroups.stream()
                        .filter(candidate -> !candidate.equals(rawGroup))
                        .filter(candidate -> rawGroup.startsWith(candidate + "_"))
                        .max(java.util.Comparator.comparingInt(String::length))
                        .orElse(rawGroup)
        ));
    }
}
