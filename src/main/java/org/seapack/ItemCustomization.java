package org.seapack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.enchantments.Enchantment;

public record ItemCustomization(
        Optional<String> displayName,
        Optional<String> nameSuffix,
        Optional<List<String>> lore,
        Map<Enchantment, Integer> enchantments
) {
    public static final ItemCustomization EMPTY = new ItemCustomization(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of()
    );
}
