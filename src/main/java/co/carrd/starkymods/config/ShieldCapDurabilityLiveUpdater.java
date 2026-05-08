package co.carrd.starkymods.config;

import co.carrd.starkymods.util.ShieldCapInventoryCompat;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class ShieldCapDurabilityLiveUpdater {
    private static final String PRIMARY_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String LEFT_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String VIBRANIUM_PRIMARY_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String CARTER_PRIMARY_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String CARTER_LEFT_ID = "Weapon_ShieldLeft_CaptainCarter_Starky";
    private static final String THROWN_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final long WORLD_UPDATE_WAIT_TIMEOUT_MS = 15000L;

    private ShieldCapDurabilityLiveUpdater() {
    }

    public static int applyEverywhereLoaded(Integer configuredMaxDurability) {
        if (configuredMaxDurability == null) {
            return 0;
        }

        int newMaxDurability = Math.max(0, configuredMaxDurability);
        Universe universe = Universe.get();
        if (universe == null) {
            return 0;
        }

        int updatedStacks = 0;
        updatedStacks += applyToOnlinePlayers(newMaxDurability);
        updatedStacks += applyToLoadedWorldData(universe, newMaxDurability);
        return updatedStacks;
    }

    public static int applyToOnlinePlayers(Integer configuredMaxDurability) {
        if (configuredMaxDurability == null) {
            return 0;
        }

        return applyToOnlinePlayers(Math.max(0, configuredMaxDurability));
    }

    public static int applyToPlayerRef(PlayerRef playerRef, Integer configuredMaxDurability) {
        if (playerRef == null || configuredMaxDurability == null) {
            return 0;
        }
        return applyToPlayerThreadSafe(playerRef, Math.max(0, configuredMaxDurability));
    }

    private static int applyToOnlinePlayers(int newMaxDurability) {
        Universe universe = Universe.get();
        if (universe == null) {
            return 0;
        }

        int updatedStacks = 0;
        for (PlayerRef playerRef : universe.getPlayers()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            try {
                updatedStacks += applyToPlayerThreadSafe(playerRef, newMaxDurability);
            } catch (Throwable t) {
                System.out.println("[ShieldCap] Error updating player inventory for " + playerRef.getUuid() + ": " + t.getMessage());
                t.printStackTrace();
            }
        }
        return updatedStacks;
    }

    private static int applyToLoadedWorldData(Universe universe, int newMaxDurability) {
        Map<String, World> worlds = universe.getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (World world : worlds.values()) {
            if (world == null || !world.isAlive()) {
                continue;
            }

            if (world.isInThread()) {
                updated += applyToWorldData(world, newMaxDurability);
                continue;
            }

            AtomicInteger worldUpdated = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            world.execute(() -> {
                try {
                    worldUpdated.set(applyToWorldData(world, newMaxDurability));
                } catch (Throwable t) {
                    System.out.println("[ShieldCap] Error updating durability in world " + world.getName() + ": " + t.getMessage());
                    t.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });

            boolean completed = false;
            try {
                completed = latch.await(WORLD_UPDATE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (!completed) {
                System.out.println("[ShieldCap] Timeout updating world data in world " + world.getName() + ".");
            }
            updated += worldUpdated.get();
        }
        return updated;
    }

    private static int applyToPlayerThreadSafe(PlayerRef playerRef, int newMaxDurability) {
        if (playerRef == null || !playerRef.isValid()) {
            return 0;
        }

        World world = resolvePlayerWorld(playerRef);
        if (world == null || world.isInThread()) {
            return applyToPlayer(playerRef, newMaxDurability);
        }

        AtomicInteger updated = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        world.execute(() -> {
            try {
                updated.set(applyToPlayer(playerRef, newMaxDurability));
            } finally {
                latch.countDown();
            }
        });

        boolean completed = false;
        try {
            completed = latch.await(WORLD_UPDATE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!completed) {
            System.out.println("[ShieldCap] Timeout updating player inventory for " + playerRef.getUuid() + ".");
        }
        return updated.get();
    }

    private static World resolvePlayerWorld(PlayerRef playerRef) {
        Holder<EntityStore> holder = playerRef.getHolder();
        if (holder != null) {
            Player player = holder.getComponent(Player.getComponentType());
            if (player != null && player.getWorld() != null) {
                return player.getWorld();
            }
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef != null && entityRef.isValid()) {
            Store<EntityStore> entityStore = entityRef.getStore();
            if (entityStore != null) {
                EntityStore externalData = entityStore.getExternalData();
                if (externalData != null) {
                    return externalData.getWorld();
                }
            }
        }
        return null;
    }

    private static int applyToWorldData(World world, int newMaxDurability) {
        return applyToDroppedItemEntities(world, newMaxDurability)
                + applyToBlockItemContainers(world, newMaxDurability);
    }

    private static int applyToDroppedItemEntities(World world, int newMaxDurability) {
        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            return 0;
        }

        Store<EntityStore> store = entityStore.getStore();
        if (store == null) {
            return 0;
        }

        AtomicInteger updated = new AtomicInteger(0);
        var itemType = ItemComponent.getComponentType();
        Query<EntityStore> query = Archetype.of(itemType);
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkUpdater = (chunk, cmd) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                ItemComponent itemComponent = chunk.getComponent(i, itemType);
                if (itemComponent == null) {
                    continue;
                }

                ItemStack stack = itemComponent.getItemStack();
                ItemStack updatedStack = buildUpdatedStackIfNeeded(stack, newMaxDurability);
                if (updatedStack == null) {
                    continue;
                }

                itemComponent.setItemStack(updatedStack);
                updated.incrementAndGet();
            }
        };
        store.forEachChunk(query, chunkUpdater);
        return updated.get();
    }

    private static int applyToBlockItemContainers(World world, int newMaxDurability) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return 0;
        }

        BlockModule blockModule = BlockModule.get();
        if (blockModule == null) {
            return 0;
        }

        ResourceType<ChunkStore, SpatialResource<Ref<ChunkStore>, ChunkStore>> resourceType =
                blockModule.getItemContainerSpatialResourceType();
        if (resourceType == null) {
            return 0;
        }

        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return 0;
        }

        SpatialResource<Ref<ChunkStore>, ChunkStore> spatial = store.getResource(resourceType);
        if (spatial == null) {
            return 0;
        }

        SpatialData<Ref<ChunkStore>> data = spatial.getSpatialData();
        if (data == null || data.size() == 0) {
            return 0;
        }

        int updated = 0;
        var itemContainerBlockType = blockModule.getItemContainerBlockComponentType();
        for (int i = 0; i < data.size(); i++) {
            Ref<ChunkStore> stateRef = data.getData(i);
            if (stateRef == null || !stateRef.isValid()) {
                continue;
            }

            ItemContainerBlock itemContainerBlock = store.getComponent(stateRef, itemContainerBlockType);
            if (itemContainerBlock == null) {
                continue;
            }

            updated += applyToContainer(itemContainerBlock.getItemContainer(), newMaxDurability);
        }
        return updated;
    }

    private static int applyToPlayer(PlayerRef playerRef, int newMaxDurability) {
        Holder<EntityStore> holder = playerRef.getHolder();
        Player player = null;
        if (holder != null) {
            player = holder.getComponent(Player.getComponentType());
        }
        if (player == null) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef != null && entityRef.isValid()) {
                Store<EntityStore> entityStore = entityRef.getStore();
                if (entityStore != null) {
                    player = entityStore.getComponent(entityRef, Player.getComponentType());
                }
            }
        }
        if (player == null) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return 0;
        }

        int updated = 0;
        updated += applyToContainer(inventory.getHotbar(), newMaxDurability);
        updated += applyToContainer(inventory.getStorage(), newMaxDurability);
        updated += applyToContainer(inventory.getBackpack(), newMaxDurability);
        updated += applyToContainer(inventory.getUtility(), newMaxDurability);
        updated += applyToContainer(inventory.getTools(), newMaxDurability);
        updated += applyToContainer(inventory.getArmor(), newMaxDurability);

        ItemContainer everything = ShieldCapInventoryCompat.getCombinedEverything(inventory);
        updated += applyToContainer(everything, newMaxDurability);
        return updated;
    }

    private static int applyToContainer(ItemContainer container, int newMaxDurability) {
        if (container == null) {
            return 0;
        }

        int updated = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            ItemStack updatedStack = buildUpdatedStackIfNeeded(stack, newMaxDurability);
            if (updatedStack == null) {
                continue;
            }
            container.setItemStackForSlot(slot, updatedStack);
            updated++;
        }
        return updated;
    }

    private static ItemStack buildUpdatedStackIfNeeded(ItemStack stack, int newMaxDurability) {
        if (stack == null || stack.isEmpty() || !isShieldCapStack(stack.getItemId())) {
            return null;
        }

        double oldMax = Math.max(0.0, stack.getMaxDurability());
        double oldDurability = Math.max(0.0, stack.getDurability());

        double newDurability;
        if (oldDurability <= 0.0) {
            newDurability = (newMaxDurability <= 0) ? 0.0 : newMaxDurability;
        } else if (oldMax > 0.0) {
            newDurability = (oldDurability / oldMax) * newMaxDurability;
        } else {
            newDurability = newMaxDurability;
        }
        newDurability = Math.max(0.0, Math.min(newDurability, newMaxDurability));

        boolean maxChanged = Math.abs(oldMax - newMaxDurability) > 1e-6;
        boolean durabilityChanged = Math.abs(oldDurability - newDurability) > 1e-6;
        if (!maxChanged && !durabilityChanged) {
            return null;
        }

        ItemStack updatedStack = stack.withMaxDurability(newMaxDurability).withDurability(newDurability);
        BsonDocument originalMetadata = extractMetadataFromPacket(stack);
        if (originalMetadata != null) {
            updatedStack = updatedStack.withMetadata(originalMetadata);
        }
        updatedStack.setOverrideDroppedItemAnimation(stack.getOverrideDroppedItemAnimation());
        return updatedStack;
    }

    private static BsonDocument extractMetadataFromPacket(ItemStack stack) {
        String rawMetadata = stack.toPacket().metadata;
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return null;
        }
        return BsonDocument.parse(rawMetadata);
    }

    private static boolean isShieldCapStack(String itemId) {
        return isMatchingItemId(itemId, PRIMARY_ID)
                || isMatchingItemId(itemId, LEFT_ID)
                || isMatchingItemId(itemId, VIBRANIUM_PRIMARY_ID)
                || isMatchingItemId(itemId, VIBRANIUM_LEFT_ID)
                || isMatchingItemId(itemId, CARTER_PRIMARY_ID)
                || isMatchingItemId(itemId, CARTER_LEFT_ID)
                || isMatchingItemId(itemId, THROWN_ID);
    }

    private static boolean isMatchingItemId(String itemId, String baseId) {
        return itemId != null
                && !itemId.isBlank()
                && (baseId.equals(itemId) || itemId.endsWith("." + baseId) || itemId.contains(baseId));
    }
}
