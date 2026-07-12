package org.seapack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

public record ArmorRendering(
        Color leatherColor,
        String equipmentAssetId,
        EquipmentSlot slot,
        Material preferredMaterial
) {
    public static final ArmorRendering NONE = new ArmorRendering(null, "", null, null);

    public boolean hasLeatherColor() {
        return leatherColor != null;
    }

    public boolean hasEquipmentAsset() {
        return equipmentAssetId != null && !equipmentAssetId.isBlank() && slot != null;
    }
}
