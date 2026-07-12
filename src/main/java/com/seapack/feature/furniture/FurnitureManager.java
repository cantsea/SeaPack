package com.seapack.feature.furniture;

import com.seapack.SeaPack;
import com.seapack.feature.item.CustomItemDefinition;
import com.seapack.feature.item.ItemManager;
import com.seapack.service.FurnitureProtection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class FurnitureManager {
    private final SeaPack plugin;
    private final ItemManager itemManager;
    private final FurnitureProtection protection;
    private final FurniturePersistence persistence;
    private final FurnitureBlocks blocks;
    private final FurnitureTargeting targeting;
    private final Set<UUID> placementProtectedFurniture = new HashSet<>();
    private final Map<UUID, Object> recentSuccessfulPlacements = new HashMap<>();

    public FurnitureManager(
            SeaPack plugin,
            ItemManager itemManager,
            FurnitureProtection protection
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.protection = protection;
        this.persistence = new FurniturePersistence(plugin);
        this.blocks = new FurnitureBlocks(persistence);
        this.targeting = new FurnitureTargeting();
    }

    public void handlePlacement(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }

        ItemStack heldItem = event.getItem();
        CustomItemDefinition definition = itemManager.identifyFurniture(heldItem, plugin.itemIdKey());
        if (definition == null) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && itemManager.identifyFurniture(
                        event.getPlayer().getInventory().getItemInMainHand(),
                        plugin.itemIdKey()
                ) != null) {
            return;
        }

        FurnitureSettings settings = itemManager.furniture(definition.id());
        if (settings == null || !settings.enabled() || !settings.hasPlacementSurface()) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        FurniturePlacement.ResolvedPlacement placement = FurniturePlacement.resolve(event, player, settings);
        if (placement == null) {
            Block attemptedAnchor = event.getClickedBlock() == null
                    ? null
                    : event.getClickedBlock().getRelative(event.getBlockFace());
            sendSurfaceErrorIfStillMissing(player, attemptedAnchor);
            return;
        }

        Block targetBlock = placement.anchorBlock();
        if (!protection.canPlace(player, targetBlock.getLocation())) {
            player.sendMessage(Component.text("You cannot place furniture here.", NamedTextColor.RED));
            return;
        }

        List<FurnitureGeometry.BlockPosition> solidBlocks = settings.solid()
                ? FurnitureGeometry.occupiedBlocks(placement.location(), placement.visualYaw(), settings)
                : List.of();
        if (!blocks.canPlaceAt(targetBlock, solidBlocks) || hasFurnitureAt(targetBlock)) {
            player.sendMessage(Component.text("There is not enough room to place that here.", NamedTextColor.RED));
            return;
        }

        Block lightBlock = blocks.chooseLightBlock(targetBlock, solidBlocks, settings.lightLevel());
        if (settings.lightLevel() > 0 && lightBlock == null) {
            player.sendMessage(Component.text(
                    "There is not enough room for this furniture's light.",
                    NamedTextColor.RED
            ));
            return;
        }

        ItemStack placedItem = heldItem.clone();
        placedItem.setAmount(1);
        ArmorStand armorStand = spawnFurniture(
                placement.location(),
                placement.entityYaw(),
                placedItem,
                definition.id(),
                targetBlock,
                lightBlock == null ? targetBlock : lightBlock,
                solidBlocks,
                settings
        );

        blocks.ensureSolidBlocks(targetBlock, solidBlocks);
        blocks.ensureLight(lightBlock, settings.lightLevel());
        rememberSuccessfulPlacement(player);
        consumeOne(player, event.getHand(), heldItem);
        schedulePlacementVerification(
                armorStand,
                placement,
                placedItem,
                definition.id(),
                targetBlock,
                lightBlock,
                solidBlocks,
                settings
        );
    }

    public void handleDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand armorStand) || !isFurniture(armorStand)) {
            return;
        }
        event.setCancelled(true);
        if (placementProtectedFurniture.contains(armorStand.getUniqueId())) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent damageByEntity)
                || !(damageByEntity.getDamager() instanceof Player player)) {
            return;
        }
        tryRemoveFurniture(armorStand, player);
    }

    public void handleManipulation(PlayerArmorStandManipulateEvent event) {
        if (isFurniture(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    public void handleSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        ArmorStand aimedFurniture = targeting.findTarget(player, this::isFurniture, this::settingsFor);
        if (aimedFurniture == null || placementProtectedFurniture.contains(aimedFurniture.getUniqueId())) {
            return;
        }
        tryRemoveFurniture(aimedFurniture, player);
    }

    public void handleDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand armorStand) || !isFurniture(armorStand)) {
            return;
        }
        placementProtectedFurniture.remove(armorStand.getUniqueId());
        event.getDrops().clear();
        blocks.removeOwnedBlocks(armorStand);
    }

    private ArmorStand spawnFurniture(
            Location location,
            float yaw,
            ItemStack placedItem,
            String itemId,
            Block targetBlock,
            Block lightBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks,
            FurnitureSettings settings
    ) {
        ArmorStand armorStand = targetBlock.getWorld().spawn(location.clone(), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(settings.small());
            stand.setGravity(settings.gravity());
            stand.setSilent(true);
            stand.setPersistent(true);
            stand.setRemoveWhenFarAway(false);
            stand.setCollidable(settings.solid());
            stand.setCanPickupItems(false);
            stand.setHeadPose(FurniturePlacement.headPose(settings.modelTransform()));
            stand.getEquipment().setHelmet(placedItem.clone());
            persistence.tag(
                    stand,
                    itemId,
                    targetBlock,
                    lightBlock,
                    settings.lightLevel(),
                    solidBlocks
            );
        });
        armorStand.setRotation(yaw, 0.0f);
        if (!settings.gravity()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (armorStand.isValid()) {
                    armorStand.setRotation(yaw, 0.0f);
                    armorStand.setCanTick(false);
                }
            }, 2L);
        }
        protectPlacementAnimation(armorStand);
        return armorStand;
    }

    private void schedulePlacementVerification(
            ArmorStand armorStand,
            FurniturePlacement.ResolvedPlacement placement,
            ItemStack placedItem,
            String itemId,
            Block targetBlock,
            Block lightBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks,
            FurnitureSettings settings
    ) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!blocks.isChunkLoaded(targetBlock)) {
                return;
            }
            if (!armorStand.isValid() && !hasFurnitureAt(targetBlock)) {
                spawnFurniture(
                        placement.location(),
                        placement.entityYaw(),
                        placedItem,
                        itemId,
                        targetBlock,
                        lightBlock == null ? targetBlock : lightBlock,
                        solidBlocks,
                        settings
                );
            }
            blocks.ensureSolidBlocks(targetBlock, solidBlocks);
            blocks.ensureLight(lightBlock, settings.lightLevel());
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!blocks.isChunkLoaded(targetBlock)
                    || (!armorStand.isValid() && !hasFurnitureAt(targetBlock))) {
                return;
            }
            blocks.ensureSolidBlocks(targetBlock, solidBlocks);
            blocks.ensureLight(lightBlock, settings.lightLevel());
        }, 20L);
    }

    private void protectPlacementAnimation(ArmorStand armorStand) {
        UUID entityId = armorStand.getUniqueId();
        placementProtectedFurniture.add(entityId);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> placementProtectedFurniture.remove(entityId),
                4L
        );
    }

    private void rememberSuccessfulPlacement(Player player) {
        UUID playerId = player.getUniqueId();
        Object receipt = new Object();
        recentSuccessfulPlacements.put(playerId, receipt);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> recentSuccessfulPlacements.remove(playerId, receipt),
                4L
        );
    }

    private void sendSurfaceErrorIfStillMissing(Player player, Block attemptedAnchor) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()
                    || recentSuccessfulPlacements.containsKey(player.getUniqueId())
                    || attemptedAnchor != null && hasFurnitureAt(attemptedAnchor)) {
                return;
            }
            player.sendMessage(Component.text(
                    "This furniture cannot be placed on that surface.",
                    NamedTextColor.RED
            ));
        });
    }

    private boolean isFurniture(ArmorStand armorStand) {
        return persistence.isTagged(armorStand) || furnitureDefinition(armorStand) != null;
    }

    private CustomItemDefinition furnitureDefinition(ArmorStand armorStand) {
        return itemManager.identifyFurniture(armorStand.getEquipment().getHelmet(), plugin.itemIdKey());
    }

    private boolean hasFurnitureAt(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        return block.getWorld().getNearbyEntitiesByType(
                ArmorStand.class,
                center,
                4.5,
                4.5,
                4.5,
                armorStand -> isFurniture(armorStand) && persistence.anchoredAt(armorStand, block)
        ).stream().findAny().isPresent();
    }

    private FurnitureSettings settingsFor(ArmorStand armorStand) {
        String itemId = persistence.itemId(armorStand);
        if (itemId == null) {
            CustomItemDefinition definition = furnitureDefinition(armorStand);
            itemId = definition == null ? null : definition.id();
        }
        return itemManager.furniture(itemId);
    }

    private void tryRemoveFurniture(ArmorStand armorStand, Player player) {
        if (protection.canBreak(player, persistence.anchor(armorStand))) {
            removeFurniture(armorStand, player);
        }
    }

    private void removeFurniture(ArmorStand armorStand, Player player) {
        placementProtectedFurniture.remove(armorStand.getUniqueId());
        FurnitureSettings settings = settingsFor(armorStand);
        ItemStack storedItem = armorStand.getEquipment().getHelmet();
        Location dropLocation = armorStand.getLocation().clone().add(0.0, 0.25, 0.0);
        armorStand.getEquipment().setHelmet(null);
        blocks.removeOwnedBlocks(armorStand);
        armorStand.remove();

        boolean dropItem = settings == null || settings.dropItem();
        if (!dropItem || storedItem == null || storedItem.getType().isAir()
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        storedItem.setAmount(1);
        dropLocation.getWorld().dropItemNaturally(dropLocation, storedItem);
    }

    private static void consumeOne(Player player, EquipmentSlot hand, ItemStack heldItem) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
            return;
        }
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }
}
