package org.seapack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class FurnitureListener implements Listener {
    private static final double DEFAULT_BREAK_REACH = 4.5;
    private static final double BREAK_AIM_RADIUS = 0.9;

    private final SeaPack plugin;
    private final CustomItemRegistry itemRegistry;
    private final FurnitureProtection protection;
    private final NamespacedKey furnitureIdKey;
    private final NamespacedKey blockXKey;
    private final NamespacedKey blockYKey;
    private final NamespacedKey blockZKey;
    private final NamespacedKey lightKey;
    private final NamespacedKey lightLevelKey;
    private final NamespacedKey lightXKey;
    private final NamespacedKey lightYKey;
    private final NamespacedKey lightZKey;
    private final NamespacedKey barrierBlocksKey;
    private final Set<UUID> placementProtectedFurniture = new HashSet<>();
    private final Map<UUID, Object> recentSuccessfulPlacements = new HashMap<>();

    public FurnitureListener(
            SeaPack plugin,
            CustomItemRegistry itemRegistry,
            FurnitureProtection protection
    ) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.protection = protection;
        this.furnitureIdKey = new NamespacedKey(plugin, "furniture_id");
        this.blockXKey = new NamespacedKey(plugin, "furniture_block_x");
        this.blockYKey = new NamespacedKey(plugin, "furniture_block_y");
        this.blockZKey = new NamespacedKey(plugin, "furniture_block_z");
        this.lightKey = new NamespacedKey(plugin, "furniture_light");
        this.lightLevelKey = new NamespacedKey(plugin, "furniture_light_level");
        this.lightXKey = new NamespacedKey(plugin, "furniture_light_x");
        this.lightYKey = new NamespacedKey(plugin, "furniture_light_y");
        this.lightZKey = new NamespacedKey(plugin, "furniture_light_z");
        this.barrierBlocksKey = new NamespacedKey(plugin, "furniture_barriers");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }

        ItemStack heldItem = event.getItem();
        CustomItemDefinition definition = itemRegistry.identifyFurniture(heldItem, plugin.itemIdKey());
        if (definition == null) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND
                && itemRegistry.identifyFurniture(
                        event.getPlayer().getInventory().getItemInMainHand(),
                        plugin.itemIdKey()
                ) != null) {
            return;
        }

        FurnitureSettings settings = itemRegistry.furniture(definition.id());
        if (settings == null || !settings.enabled() || !settings.hasPlacementSurface()) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        Placement placement = resolvePlacement(event, player, settings);
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
        if (!canPlaceAt(targetBlock, solidBlocks) || hasFurnitureAt(targetBlock)) {
            player.sendMessage(Component.text("There is not enough room to place that here.", NamedTextColor.RED));
            return;
        }
        Block lightBlock = chooseLightBlock(targetBlock, solidBlocks, settings.lightLevel());
        if (settings.lightLevel() > 0 && lightBlock == null) {
            player.sendMessage(Component.text("There is not enough room for this furniture's light.", NamedTextColor.RED));
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

        ensureSolidBlocks(targetBlock, solidBlocks);
        ensureFurnitureLight(lightBlock, settings.lightLevel());
        rememberSuccessfulPlacement(player);
        consumeOne(player, event.getHand(), heldItem);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isPlacementChunkLoaded(targetBlock)) {
                return;
            }
            if (!armorStand.isValid() && !hasFurnitureAt(targetBlock)) {
                spawnFurniture(
                        placement.location(),
                        placement.entityYaw(),
                        placedItem,
                        definition.id(),
                        targetBlock,
                        lightBlock == null ? targetBlock : lightBlock,
                        solidBlocks,
                        settings
                );
            }
            ensureSolidBlocks(targetBlock, solidBlocks);
            ensureFurnitureLight(lightBlock, settings.lightLevel());
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isPlacementChunkLoaded(targetBlock)
                    || (!armorStand.isValid() && !hasFurnitureAt(targetBlock))) {
                return;
            }
            ensureSolidBlocks(targetBlock, solidBlocks);
            ensureFurnitureLight(lightBlock, settings.lightLevel());
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureDamage(EntityDamageEvent event) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnitureManipulate(PlayerArmorStandManipulateEvent event) {
        if (isFurniture(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double reach = interactionReach(player);
        double maximumDistance = blockingDistance(player, eye, direction, reach);
        ArmorStand aimedFurniture = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ArmorStand armorStand : player.getWorld().getNearbyEntitiesByType(
                ArmorStand.class,
                eye,
                reach,
                reach,
                reach,
                this::isFurniture
        )) {
            double distance = aimDistance(eye.toVector(), direction, armorStand, reach);
            if (distance < 0.0 || distance > maximumDistance + 0.75 || distance >= nearestDistance) {
                continue;
            }
            nearestDistance = distance;
            aimedFurniture = armorStand;
        }

        if (aimedFurniture != null) {
            if (placementProtectedFurniture.contains(aimedFurniture.getUniqueId())) {
                return;
            }
            tryRemoveFurniture(aimedFurniture, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnitureDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand armorStand) || !isFurniture(armorStand)) {
            return;
        }
        placementProtectedFurniture.remove(armorStand.getUniqueId());
        event.getDrops().clear();
        removeLight(armorStand);
        removeSolidBlocks(armorStand);
    }

    private void tagFurniture(
            ArmorStand armorStand,
            String itemId,
            Block anchorBlock,
            Block lightBlock,
            int lightLevel,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        data.set(furnitureIdKey, PersistentDataType.STRING, itemId);
        data.set(blockXKey, PersistentDataType.INTEGER, anchorBlock.getX());
        data.set(blockYKey, PersistentDataType.INTEGER, anchorBlock.getY());
        data.set(blockZKey, PersistentDataType.INTEGER, anchorBlock.getZ());
        data.set(lightKey, PersistentDataType.BYTE, lightLevel > 0 ? (byte) 1 : (byte) 0);
        data.set(lightLevelKey, PersistentDataType.INTEGER, lightLevel);
        data.set(lightXKey, PersistentDataType.INTEGER, lightBlock.getX());
        data.set(lightYKey, PersistentDataType.INTEGER, lightBlock.getY());
        data.set(lightZKey, PersistentDataType.INTEGER, lightBlock.getZ());
        if (!solidBlocks.isEmpty()) {
            String serializedBlocks = solidBlocks.stream()
                    .map(position -> position.x() + "," + position.y() + "," + position.z())
                    .collect(Collectors.joining(";"));
            data.set(barrierBlocksKey, PersistentDataType.STRING, serializedBlocks);
        }
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
            stand.setHeadPose(modelHeadPose(settings.modelTransform()));
            stand.getEquipment().setHelmet(placedItem.clone());
            tagFurniture(stand, itemId, targetBlock, lightBlock, settings.lightLevel(), solidBlocks);
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

    private static Placement resolvePlacement(
            PlayerInteractEvent event,
            Player player,
            FurnitureSettings settings
    ) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return null;
        }
        BlockFace face = event.getBlockFace();
        boolean wall = face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
        boolean allowed = face == BlockFace.UP && settings.floorPlacement()
                || face == BlockFace.DOWN && settings.ceilingPlacement()
                || wall && settings.wallPlacement();
        if (!allowed) {
            return null;
        }

        Block anchorBlock = clickedBlock.getRelative(face);
        Location location = anchorBlock.getLocation().add(0.5, settings.yOffset(), 0.5);
        float yaw = facingPositionYaw(location, player.getLocation(), player.getLocation().getYaw());
        if (settings.fixedRotation()) {
            yaw = snapYaw(yaw, settings.rotationSnap());
        }
        if (settings.oppositeDirection()) {
            yaw += 180.0f;
        }
        yaw = normalizeYaw(yaw);
        FurnitureModelTransform transform = settings.modelTransform();
        float entityYaw = modelEntityYaw(yaw, transform.sourceRotation().y());
        float visualYaw = modelVisualYaw(entityYaw, transform.rotation().y());
        applyModelTranslationOverride(location, visualYaw, transform.translationDelta());
        location.setYaw(entityYaw);
        return new Placement(anchorBlock, location, entityYaw, visualYaw);
    }

    static float facingPositionYaw(Location anchor, Location player, float fallbackPlayerYaw) {
        double relativeX = player.getX() - anchor.getX();
        double relativeZ = player.getZ() - anchor.getZ();
        if (relativeX * relativeX + relativeZ * relativeZ < 1.0E-8) {
            return fallbackPlayerYaw + 180.0f;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-relativeX, relativeZ));
        return yaw == 0.0f ? 0.0f : yaw;
    }

    static float snapYaw(float yaw, int rotationSnap) {
        int step = rotationSnap == 90 ? 90 : 45;
        return normalizeYaw(Math.round(yaw / step) * (float) step);
    }

    static float modelEntityYaw(float desiredVisualYaw, double sourceModelYaw) {
        return normalizeYaw((float) (desiredVisualYaw - sourceModelYaw));
    }

    static float modelVisualYaw(float entityYaw, double configuredModelYaw) {
        return normalizeYaw((float) (entityYaw + configuredModelYaw));
    }

    static void applyModelTranslationOverride(
            Location location,
            float visualYaw,
            FurnitureModelTransform.Values translationDelta
    ) {
        double localX = translationDelta.x() / 16.0;
        double localY = translationDelta.y() / 16.0;
        double localZ = translationDelta.z() / 16.0;
        double radians = Math.toRadians(visualYaw);
        double sine = Math.sin(radians);
        double cosine = Math.cos(radians);
        location.add(
                localX * cosine - localZ * sine,
                localY,
                localX * sine + localZ * cosine
        );
    }

    private static EulerAngle modelHeadPose(FurnitureModelTransform transform) {
        FurnitureModelTransform.Values delta = transform.rotationDelta();
        return new EulerAngle(
                Math.toRadians(delta.x()),
                Math.toRadians(delta.y()),
                Math.toRadians(delta.z())
        );
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized <= -180.0f) {
            normalized += 360.0f;
        }
        return normalized == 0.0f ? 0.0f : normalized;
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
        if (armorStand.getPersistentDataContainer().has(furnitureIdKey, PersistentDataType.STRING)) {
            return true;
        }
        return furnitureDefinition(armorStand) != null;
    }

    private CustomItemDefinition furnitureDefinition(ArmorStand armorStand) {
        ItemStack helmet = armorStand.getEquipment().getHelmet();
        return itemRegistry.identifyFurniture(helmet, plugin.itemIdKey());
    }

    private boolean hasFurnitureAt(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        return block.getWorld().getNearbyEntitiesByType(
                ArmorStand.class,
                center,
                4.5,
                4.5,
                4.5,
                armorStand -> isFurniture(armorStand) && anchoredAt(armorStand, block)
        ).stream().findAny().isPresent();
    }

    private boolean anchoredAt(ArmorStand armorStand, Block block) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        Integer x = data.get(blockXKey, PersistentDataType.INTEGER);
        Integer y = data.get(blockYKey, PersistentDataType.INTEGER);
        Integer z = data.get(blockZKey, PersistentDataType.INTEGER);
        if (x != null && y != null && z != null) {
            return x == block.getX() && y == block.getY() && z == block.getZ();
        }
        return armorStand.getLocation().distanceSquared(block.getLocation().add(0.5, 0.0, 0.5)) <= 1.0;
    }

    private static double interactionReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return attribute == null ? DEFAULT_BREAK_REACH : Math.max(0.0, attribute.getValue());
    }

    private static double blockingDistance(Player player, Location eye, Vector direction, double reach) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye,
                direction,
                reach,
                FluidCollisionMode.NEVER,
                true
        );
        return result == null || result.getHitPosition() == null
                ? reach
                : eye.toVector().distance(result.getHitPosition());
    }

    private double aimDistance(Vector origin, Vector direction, ArmorStand armorStand, double reach) {
        FurnitureSettings settings = settingsFor(armorStand);
        if (settings != null) {
            return FurnitureGeometry.rayDistance(
                    origin,
                    direction,
                    armorStand.getLocation(),
                    modelVisualYaw(
                            armorStand.getLocation().getYaw(),
                            settings.modelTransform().rotation().y()
                    ),
                    settings,
                    reach
            );
        }

        Location standLocation = armorStand.getLocation();
        double[] heights = armorStand.isSmall()
                ? new double[]{0.2, 0.5, 0.85}
                : new double[]{0.25, 0.8, 1.45};
        double bestProjection = -1.0;

        for (double height : heights) {
            Vector target = standLocation.toVector().add(new Vector(0.0, height, 0.0));
            Vector offset = target.clone().subtract(origin);
            double projection = offset.dot(direction);
            if (projection < 0.0 || projection > reach) {
                continue;
            }
            Vector closestPoint = origin.clone().add(direction.clone().multiply(projection));
            if (closestPoint.distanceSquared(target) <= BREAK_AIM_RADIUS * BREAK_AIM_RADIUS
                    && (bestProjection < 0.0 || projection < bestProjection)) {
                bestProjection = projection;
            }
        }
        return bestProjection;
    }

    private FurnitureSettings settingsFor(ArmorStand armorStand) {
        String itemId = armorStand.getPersistentDataContainer()
                .get(furnitureIdKey, PersistentDataType.STRING);
        if (itemId == null) {
            CustomItemDefinition definition = furnitureDefinition(armorStand);
            itemId = definition == null ? null : definition.id();
        }
        return itemRegistry.furniture(itemId);
    }

    private static boolean canPlaceAt(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        if (!canUsePlacementBlock(anchorBlock)) {
            return false;
        }
        return solidBlocks.stream()
                .map(position -> anchorBlock.getWorld().getBlockAt(position.x(), position.y(), position.z()))
                .allMatch(FurnitureListener::canUsePlacementBlock);
    }

    private static Block chooseLightBlock(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks,
            int lightLevel
    ) {
        if (lightLevel <= 0) {
            return anchorBlock;
        }
        Set<FurnitureGeometry.BlockPosition> occupied = new HashSet<>(solidBlocks);
        FurnitureGeometry.BlockPosition anchorPosition = new FurnitureGeometry.BlockPosition(
                anchorBlock.getX(),
                anchorBlock.getY(),
                anchorBlock.getZ()
        );
        if (!occupied.contains(anchorPosition) && canUsePlacementBlock(anchorBlock)) {
            return anchorBlock;
        }
        for (int offset = 1; offset <= 4; offset++) {
            Block candidate = anchorBlock.getRelative(BlockFace.UP, offset);
            FurnitureGeometry.BlockPosition position = new FurnitureGeometry.BlockPosition(
                    candidate.getX(),
                    candidate.getY(),
                    candidate.getZ()
            );
            if (!occupied.contains(position) && canUsePlacementBlock(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void ensureSolidBlocks(
            Block anchorBlock,
            List<FurnitureGeometry.BlockPosition> solidBlocks
    ) {
        for (FurnitureGeometry.BlockPosition position : solidBlocks) {
            Block block = anchorBlock.getWorld().getBlockAt(position.x(), position.y(), position.z());
            if (canUsePlacementBlock(block)) {
                block.setType(Material.BARRIER, false);
            }
        }
    }

    private static boolean canUsePlacementBlock(Block block) {
        return block.getType().isAir() || block.getType() == Material.LIGHT;
    }

    private static void placeLight(Block block, int lightLevel) {
        BlockData blockData = Material.LIGHT.createBlockData();
        if (blockData instanceof Levelled levelled) {
            levelled.setLevel(Math.max(levelled.getMinimumLevel(), Math.min(levelled.getMaximumLevel(), lightLevel)));
        }
        block.setBlockData(blockData, false);
    }

    private static void ensureFurnitureLight(Block block, int lightLevel) {
        if (lightLevel <= 0 || !canUsePlacementBlock(block)) {
            return;
        }
        if (block.getType() == Material.LIGHT && matchesLightLevel(block, lightLevel)) {
            return;
        }
        placeLight(block, lightLevel);
    }

    private static boolean isPlacementChunkLoaded(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private void tryRemoveFurniture(ArmorStand armorStand, Player player) {
        if (!protection.canBreak(player, furnitureAnchor(armorStand))) {
            return;
        }
        removeFurniture(armorStand, player);
    }

    private Location furnitureAnchor(ArmorStand armorStand) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        Integer x = data.get(blockXKey, PersistentDataType.INTEGER);
        Integer y = data.get(blockYKey, PersistentDataType.INTEGER);
        Integer z = data.get(blockZKey, PersistentDataType.INTEGER);
        if (x == null || y == null || z == null) {
            return armorStand.getLocation();
        }
        return new Location(armorStand.getWorld(), x, y, z);
    }

    private void removeFurniture(ArmorStand armorStand, Player player) {
        placementProtectedFurniture.remove(armorStand.getUniqueId());
        String itemId = armorStand.getPersistentDataContainer()
                .get(furnitureIdKey, PersistentDataType.STRING);
        if (itemId == null) {
            CustomItemDefinition definition = furnitureDefinition(armorStand);
            itemId = definition == null ? null : definition.id();
        }
        FurnitureSettings settings = itemRegistry.furniture(itemId);
        ItemStack storedItem = armorStand.getEquipment().getHelmet();
        Location dropLocation = armorStand.getLocation().clone().add(0.0, 0.25, 0.0);
        armorStand.getEquipment().setHelmet(null);
        removeLight(armorStand);
        removeSolidBlocks(armorStand);
        armorStand.remove();

        boolean dropItem = settings == null || settings.dropItem();
        if (!dropItem || storedItem == null || storedItem.getType().isAir()
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        storedItem.setAmount(1);
        dropLocation.getWorld().dropItemNaturally(dropLocation, storedItem);
    }

    private void removeLight(ArmorStand armorStand) {
        PersistentDataContainer data = armorStand.getPersistentDataContainer();
        Byte hasLight = data.get(lightKey, PersistentDataType.BYTE);
        Integer expectedLightLevel = data.get(lightLevelKey, PersistentDataType.INTEGER);
        Integer x = data.get(lightXKey, PersistentDataType.INTEGER);
        Integer y = data.get(lightYKey, PersistentDataType.INTEGER);
        Integer z = data.get(lightZKey, PersistentDataType.INTEGER);
        if (x == null || y == null || z == null) {
            x = data.get(blockXKey, PersistentDataType.INTEGER);
            y = data.get(blockYKey, PersistentDataType.INTEGER);
            z = data.get(blockZKey, PersistentDataType.INTEGER);
        }
        if (hasLight == null || hasLight == 0 || x == null || y == null || z == null) {
            return;
        }
        Block block = armorStand.getWorld().getBlockAt(x, y, z);
        if (block.getType() == Material.LIGHT && matchesLightLevel(block, expectedLightLevel)) {
            block.setType(Material.AIR, false);
        }
    }

    private void removeSolidBlocks(ArmorStand armorStand) {
        String serialized = armorStand.getPersistentDataContainer()
                .get(barrierBlocksKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isBlank()) {
            return;
        }
        for (String encodedPosition : serialized.split(";")) {
            String[] coordinates = encodedPosition.split(",", -1);
            if (coordinates.length != 3) {
                continue;
            }
            try {
                Block block = armorStand.getWorld().getBlockAt(
                        Integer.parseInt(coordinates[0]),
                        Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2])
                );
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static boolean matchesLightLevel(Block block, Integer expectedLightLevel) {
        if (expectedLightLevel == null) {
            return true;
        }
        BlockData blockData = block.getBlockData();
        return blockData instanceof Levelled levelled && levelled.getLevel() == expectedLightLevel;
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

    private record Placement(Block anchorBlock, Location location, float entityYaw, float visualYaw) {
    }
}
