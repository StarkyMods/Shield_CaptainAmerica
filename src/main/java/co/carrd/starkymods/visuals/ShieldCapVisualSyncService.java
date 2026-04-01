package co.carrd.starkymods.visuals;

import co.carrd.starkymods.interactions.ShieldCapThrowHomingService;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCapVisualSyncService {
    private static final String BASE_SHIELD_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String RIGHT_SHIELD_ID = "Weapon_ShieldRight_CaptainAmerica_Starky";
    private static final String LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";

    private final Set<UUID> syncingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSyncPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> forceBackRebuildPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>>> inventoryContainerRegistrations =
            new ConcurrentHashMap<>();

    private EventRegistration<String, PlayerReadyEvent> playerReadyRegistration;
    private EventRegistration<Void, PlayerDisconnectEvent> disconnectRegistration;

    public void register(JavaPlugin plugin) {
        playerReadyRegistration =
                plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::handlePlayerReady);
        disconnectRegistration = plugin.getEventRegistry().register(PlayerDisconnectEvent.class, this::handleDisconnect);
    }

    public void shutdown() {
        if (playerReadyRegistration != null) {
            playerReadyRegistration.unregister();
            playerReadyRegistration = null;
        }
        if (disconnectRegistration != null) {
            disconnectRegistration.unregister();
            disconnectRegistration = null;
        }
        syncingPlayers.clear();
        pendingSyncPlayers.clear();
        forceBackRebuildPlayers.clear();
        inventoryContainerRegistrations.values().forEach(this::unregisterContainerListeners);
        inventoryContainerRegistrations.clear();
    }

    public void syncNow(Player player) {
        sync(player, null, null);
    }

    public void syncDeferred(Player player) {
        syncDeferred(player, false);
    }

    public void syncDeferred(Player player, boolean forceBackRebuild) {
        if (player == null) {
            return;
        }

        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        if (forceBackRebuild) {
            forceBackRebuildPlayers.add(playerUuid);
        }

        if (!pendingSyncPlayers.add(playerUuid)) {
            return;
        }

        World world = resolvePlayerWorld(playerRef);
        if (world == null) {
            try {
                sync(player, null, null, forceBackRebuildPlayers.remove(playerUuid));
            } finally {
                pendingSyncPlayers.remove(playerUuid);
            }
            return;
        }

        world.execute(() -> {
            try {
                Player resolvedPlayer = resolvePlayer(playerRef);
                if (resolvedPlayer != null) {
                    sync(resolvedPlayer, null, null, forceBackRebuildPlayers.remove(playerUuid));
                }
            } finally {
                pendingSyncPlayers.remove(playerUuid);
            }
        });
    }

    private void handlePlayerReady(PlayerReadyEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }

        registerInventoryListeners(event.getPlayer());
        syncDeferred(event.getPlayer(), true);
    }

    private void handleDisconnect(PlayerDisconnectEvent event) {
        if (event == null || event.getPlayerRef() == null) {
            return;
        }

        syncingPlayers.remove(event.getPlayerRef().getUuid());
        pendingSyncPlayers.remove(event.getPlayerRef().getUuid());
        forceBackRebuildPlayers.remove(event.getPlayerRef().getUuid());
        unregisterInventoryListeners(event.getPlayerRef().getUuid());
    }

    private void scheduleSync(PlayerRef playerRef, Integer hotbarOverride, Integer utilityOverride) {
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        World world = resolvePlayerWorld(playerRef);
        Runnable syncTask = () -> {
            Player player = resolvePlayer(playerRef);
            if (player != null) {
                sync(player, hotbarOverride, utilityOverride, false);
            }
        };

        if (world == null || world.isInThread()) {
            syncTask.run();
            return;
        }

        world.execute(syncTask);
    }

    private void sync(Player player, Integer hotbarOverride, Integer utilityOverride) {
        sync(player, hotbarOverride, utilityOverride, false);
    }

    private void sync(Player player,
                      Integer hotbarOverride,
                      Integer utilityOverride,
                      boolean forceBackRebuild) {
        if (player == null) {
            return;
        }

        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null || !syncingPlayers.add(playerUuid)) {
            return;
        }

        try {
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return;
            }

            ItemContainer hotbar = inventory.getHotbar();
            ItemContainer utility = inventory.getUtility();
            ItemContainer storage = inventory.getStorage();
            ItemContainer backpack = inventory.getBackpack();
            ItemContainer tools = inventory.getTools();

            byte activeHotbarSlot =
                    hotbarOverride != null ? (byte) hotbarOverride.intValue() : inventory.getActiveHotbarSlot();
            byte activeUtilitySlot =
                    utilityOverride != null ? (byte) utilityOverride.intValue() : inventory.getActiveUtilitySlot();

            boolean inventoryChanged = false;
            inventoryChanged |= normalizeActiveSlotItem(hotbar, activeHotbarSlot, BASE_SHIELD_ID);
            inventoryChanged |= normalizeActiveSlotItem(utility, activeUtilitySlot, LEFT_SHIELD_ID);
            inventoryChanged |= normalizeInactiveSlotItems(hotbar, activeHotbarSlot);
            inventoryChanged |= normalizeInactiveSlotItems(utility, activeUtilitySlot);
            inventoryChanged |= normalizeContainerItems(storage);
            inventoryChanged |= normalizeContainerItems(backpack);
            inventoryChanged |= normalizeContainerItems(tools);

            boolean shieldInHands =
                    isShieldInEquippedSlot(hotbar, activeHotbarSlot)
                            || isShieldInEquippedSlot(utility, activeUtilitySlot);
            boolean shieldInInventory =
                    containsShield(hotbar)
                            || containsShield(utility)
                            || containsShield(storage)
                            || containsShield(backpack)
                            || containsShield(tools);

            syncBackAttachment(player, shieldInInventory && !shieldInHands, forceBackRebuild);

        } finally {
            syncingPlayers.remove(playerUuid);
        }
    }

    private boolean normalizeActiveSlotItem(ItemContainer container, byte activeSlot, String desiredId) {
        if (container == null || activeSlot == Inventory.INACTIVE_SLOT_INDEX || activeSlot < 0 || activeSlot >= container.getCapacity()) {
            return false;
        }

        ItemStack current = container.getItemStack(activeSlot);
        if (!isShield(current) || matchesId(current, desiredId)) {
            return false;
        }

        container.setItemStackForSlot(activeSlot, remapItem(current, desiredId));
        return true;
    }

    private boolean normalizeInactiveSlotItems(ItemContainer container, byte activeSlot) {
        if (container == null) {
            return false;
        }

        boolean changed = false;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (slot == activeSlot) {
                continue;
            }

            ItemStack current = container.getItemStack(slot);
            if (!isHandVariant(current)) {
                continue;
            }

            container.setItemStackForSlot(slot, remapItem(current, BASE_SHIELD_ID));
            changed = true;
        }
        return changed;
    }

    private boolean normalizeContainerItems(ItemContainer container) {
        if (container == null) {
            return false;
        }

        boolean changed = false;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack current = container.getItemStack(slot);
            if (!isHandVariant(current)) {
                continue;
            }

            container.setItemStackForSlot(slot, remapItem(current, BASE_SHIELD_ID));
            changed = true;
        }
        return changed;
    }

    private boolean containsShield(ItemContainer container) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (isShield(container.getItemStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isShieldInEquippedSlot(ItemContainer container, byte activeSlot) {
        if (container == null || activeSlot == Inventory.INACTIVE_SLOT_INDEX || activeSlot < 0 || activeSlot >= container.getCapacity()) {
            return false;
        }

        return isShield(container.getItemStack((short) activeSlot));
    }

    private void syncBackAttachment(Player player, boolean shouldShowBackShield, boolean forceBackRebuild) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }

        ShieldCapBackStateComponent currentState =
                store.getComponent(ref, ShieldCapBackStateComponent.getComponentType());

        if (currentState == null) {
            ShieldCapBackStateComponent newState = new ShieldCapBackStateComponent();
            if (shouldShowBackShield) {
                newState.updateShowBackShield(true);
            }
            if (forceBackRebuild) {
                newState.rebuild();
            }
            store.putComponent(ref, ShieldCapBackStateComponent.getComponentType(), newState);
            return;
        }

        boolean changed = currentState.updateShowBackShield(shouldShowBackShield);
        if (forceBackRebuild) {
            currentState.rebuild();
        }

        if (changed || forceBackRebuild) {
            store.putComponent(ref, ShieldCapBackStateComponent.getComponentType(), currentState);
        }
    }

    private ItemStack remapItem(ItemStack current, String desiredId) {
        BsonDocument metadata = current.getMetadata() != null ? current.getMetadata().clone() : null;

        ItemStack remapped = new ItemStack(
                desiredId,
                current.getQuantity(),
                current.getDurability(),
                current.getMaxDurability(),
                metadata
        );
        remapped.setOverrideDroppedItemAnimation(current.getOverrideDroppedItemAnimation());
        return remapped;
    }

    private boolean isShield(ItemStack stack) {
        return matchesId(stack, BASE_SHIELD_ID)
                || matchesId(stack, RIGHT_SHIELD_ID)
                || matchesId(stack, LEFT_SHIELD_ID);
    }

    private boolean isHandVariant(ItemStack stack) {
        return matchesId(stack, RIGHT_SHIELD_ID) || matchesId(stack, LEFT_SHIELD_ID);
    }

    private Player resolvePlayer(PlayerRef playerRef) {
        Holder<EntityStore> holder = playerRef.getHolder();
        if (holder != null) {
            Player player = holder.getComponent(Player.getComponentType());
            if (player != null) {
                return player;
            }
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        return store != null ? store.getComponent(ref, Player.getComponentType()) : null;
    }

    private World resolvePlayerWorld(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }

        EntityStore entityStore = store.getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }

    private PlayerRef resolvePlayerRef(Player player) {
        if (player == null) {
            return null;
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        return store != null ? store.getComponent(ref, PlayerRef.getComponentType()) : null;
    }

    private void registerInventoryListeners(Player player) {
        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        unregisterInventoryListeners(playerUuid);

        List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>> registrations = new ArrayList<>();
        registerContainerListener(registrations, inventory.getHotbar(), playerRef);
        registerContainerListener(registrations, inventory.getUtility(), playerRef);
        registerContainerListener(registrations, inventory.getStorage(), playerRef);
        registerContainerListener(registrations, inventory.getBackpack(), playerRef);
        registerContainerListener(registrations, inventory.getTools(), playerRef);
        registerContainerListener(registrations, inventory.getArmor(), playerRef);

        if (!registrations.isEmpty()) {
            inventoryContainerRegistrations.put(playerUuid, registrations);
        }
    }

    private void registerContainerListener(List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>> registrations,
                                           ItemContainer container,
                                           PlayerRef playerRef) {
        if (container == null || playerRef == null || !playerRef.isValid()) {
            return;
        }

        EventRegistration<Void, ItemContainer.ItemContainerChangeEvent> registration =
                container.registerChangeEvent(event -> {
                    Player player = resolvePlayer(playerRef);
                    if (player != null) {
                        syncDeferred(player);
                    }
                });
        registrations.add(registration);
    }

    private void unregisterInventoryListeners(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>> registrations =
                inventoryContainerRegistrations.remove(playerUuid);
        unregisterContainerListeners(registrations);
    }

    private void unregisterContainerListeners(List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }

        for (EventRegistration<Void, ItemContainer.ItemContainerChangeEvent> registration : registrations) {
            if (registration != null) {
                registration.unregister();
            }
        }
    }

    private boolean matchesId(ItemStack stack, String baseId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        return itemId.equals(baseId)
                || itemId.endsWith("." + baseId)
                || itemId.contains(baseId);
    }
}
