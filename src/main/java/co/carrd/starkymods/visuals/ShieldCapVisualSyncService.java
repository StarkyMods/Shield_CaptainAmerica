package co.carrd.starkymods.visuals;

import co.carrd.starkymods.config.ShieldCapConfigManager;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingService;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
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
import org.bson.BsonString;

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
    private static final String VIBRANIUM_BASE_SHIELD_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String THROWN_SHIELD_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String VARIANT_METADATA_KEY = "ShieldCapVariant";
    private static final String VIBRANIUM_VARIANT_VALUE = "Vibranium";

    private final Set<UUID> syncingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSyncPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> forceBackRebuildPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BackShieldPreference> pendingBackShieldPreferences = new ConcurrentHashMap<>();
    private final Map<UUID, List<EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>>> inventoryContainerRegistrations =
            new ConcurrentHashMap<>();

    private EventRegistration<String, PlayerReadyEvent> playerReadyRegistration;
    private EventRegistration<String, AddPlayerToWorldEvent> addPlayerToWorldRegistration;
    private EventRegistration<Void, PlayerDisconnectEvent> disconnectRegistration;

    public void register(JavaPlugin plugin) {
        playerReadyRegistration =
                plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::handlePlayerReady);
        addPlayerToWorldRegistration =
                plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::handleAddPlayerToWorld);
        disconnectRegistration = plugin.getEventRegistry().register(PlayerDisconnectEvent.class, this::handleDisconnect);
    }

    public void shutdown() {
        if (playerReadyRegistration != null) {
            playerReadyRegistration.unregister();
            playerReadyRegistration = null;
        }
        if (addPlayerToWorldRegistration != null) {
            addPlayerToWorldRegistration.unregister();
            addPlayerToWorldRegistration = null;
        }
        if (disconnectRegistration != null) {
            disconnectRegistration.unregister();
            disconnectRegistration = null;
        }
        syncingPlayers.clear();
        pendingSyncPlayers.clear();
        forceBackRebuildPlayers.clear();
        pendingBackShieldPreferences.clear();
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
        syncDeferred(player, forceBackRebuild, BackShieldPreference.AUTO);
    }

    public void syncDeferred(Player player,
                             boolean forceBackRebuild,
                             BackShieldPreference backShieldPreference) {
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

        rememberPendingBackShieldPreference(playerUuid, backShieldPreference);
        if (!pendingSyncPlayers.add(playerUuid)) {
            return;
        }

        World world = resolvePlayerWorld(playerRef);
        if (world == null) {
            try {
                sync(
                        player,
                        null,
                        null,
                        forceBackRebuildPlayers.remove(playerUuid),
                        consumePendingBackShieldPreference(playerUuid)
                );
            } finally {
                pendingSyncPlayers.remove(playerUuid);
                pendingBackShieldPreferences.remove(playerUuid);
            }
            return;
        }

        world.execute(() -> {
            try {
                Player resolvedPlayer = resolvePlayer(playerRef);
                if (resolvedPlayer != null) {
                    sync(
                            resolvedPlayer,
                            null,
                            null,
                            forceBackRebuildPlayers.remove(playerUuid),
                            consumePendingBackShieldPreference(playerUuid)
                    );
                }
            } finally {
                pendingSyncPlayers.remove(playerUuid);
                pendingBackShieldPreferences.remove(playerUuid);
            }
        });
    }

    private void rememberPendingBackShieldPreference(UUID playerUuid, BackShieldPreference preference) {
        if (playerUuid == null || preference == null || preference == BackShieldPreference.AUTO) {
            return;
        }
        if (preference == BackShieldPreference.AUTO_CLEAR_PENDING) {
            pendingBackShieldPreferences.remove(playerUuid);
            return;
        }
        pendingBackShieldPreferences.put(playerUuid, preference);
    }

    private BackShieldPreference consumePendingBackShieldPreference(UUID playerUuid) {
        if (playerUuid == null) {
            return BackShieldPreference.AUTO;
        }
        return pendingBackShieldPreferences.getOrDefault(playerUuid, BackShieldPreference.AUTO);
    }

    private void handlePlayerReady(PlayerReadyEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }

        handlePlayerEnteredWorld(event.getPlayer(), true);
    }

    private void handleAddPlayerToWorld(AddPlayerToWorldEvent event) {
        if (event == null || event.getHolder() == null) {
            return;
        }

        World world = event.getWorld();
        if (world == null) {
            return;
        }

        world.execute(() -> {
            Player player = event.getHolder().getComponent(Player.getComponentType());
            if (player != null) {
                handlePlayerEnteredWorld(player, true);
            }
        });
    }

    private void handlePlayerEnteredWorld(Player player, boolean forceBackRebuild) {
        if (player == null) {
            return;
        }

        sanitizeThrownShieldItems(player);
        registerInventoryListeners(player);
        syncDeferred(player, forceBackRebuild);
    }

    private void sanitizeThrownShieldItems(Player player) {
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        sanitizeThrownShieldItems(inventory.getHotbar());
        sanitizeThrownShieldItems(inventory.getUtility());
        sanitizeThrownShieldItems(inventory.getStorage());
        sanitizeThrownShieldItems(inventory.getBackpack());
        sanitizeThrownShieldItems(inventory.getTools());
        sanitizeThrownShieldItems(inventory.getArmor());
    }

    private void sanitizeThrownShieldItems(ItemContainer container) {
        if (container == null) {
            return;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack current = container.getItemStack(slot);
            if (!matchesId(current, THROWN_SHIELD_ID)) {
                continue;
            }

            container.setItemStackForSlot(slot, remapItem(current, resolveBaseShieldId(current)));
        }
    }

    private void handleDisconnect(PlayerDisconnectEvent event) {
        if (event == null || event.getPlayerRef() == null) {
            return;
        }

        syncingPlayers.remove(event.getPlayerRef().getUuid());
        pendingSyncPlayers.remove(event.getPlayerRef().getUuid());
        forceBackRebuildPlayers.remove(event.getPlayerRef().getUuid());
        pendingBackShieldPreferences.remove(event.getPlayerRef().getUuid());
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
        sync(player, hotbarOverride, utilityOverride, false, BackShieldPreference.AUTO);
    }

    private void sync(Player player,
                      Integer hotbarOverride,
                      Integer utilityOverride,
                      boolean forceBackRebuild) {
        sync(player, hotbarOverride, utilityOverride, forceBackRebuild, BackShieldPreference.AUTO);
    }

    private void sync(Player player,
                      Integer hotbarOverride,
                      Integer utilityOverride,
                      boolean forceBackRebuild,
                      BackShieldPreference backShieldPreference) {
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
            inventoryChanged |= normalizeActiveSlotItem(
                    hotbar,
                    activeHotbarSlot,
                    BASE_SHIELD_ID,
                    VIBRANIUM_BASE_SHIELD_ID
            );
            inventoryChanged |= normalizeActiveSlotItem(
                    utility,
                    activeUtilitySlot,
                    LEFT_SHIELD_ID,
                    VIBRANIUM_LEFT_SHIELD_ID
            );
            inventoryChanged |= normalizeInactiveSlotItems(hotbar, activeHotbarSlot);
            inventoryChanged |= normalizeInactiveSlotItems(utility, activeUtilitySlot);
            inventoryChanged |= normalizeContainerItems(storage);
            inventoryChanged |= normalizeContainerItems(backpack);
            inventoryChanged |= normalizeContainerItems(tools);

            int totalShieldCount =
                    countShields(hotbar)
                            + countShields(utility)
                            + countShields(storage)
                            + countShields(backpack)
                            + countShields(tools);
            int equippedShieldCount =
                    countShieldInEquippedSlot(hotbar, activeHotbarSlot)
                            + countShieldInEquippedSlot(utility, activeUtilitySlot);
            boolean hasSpareShieldOutsideActiveHands = totalShieldCount > equippedShieldCount;
            boolean hasNormalBackShieldCandidate =
                    hasNormalShieldOutsideActiveSlot(hotbar, activeHotbarSlot)
                            || hasNormalShieldOutsideActiveSlot(utility, activeUtilitySlot)
                            || hasNormalShield(storage)
                            || hasNormalShield(backpack)
                            || hasNormalShield(tools);
            boolean hasVibraniumBackShieldCandidate =
                    hasVibraniumShieldOutsideActiveSlot(hotbar, activeHotbarSlot)
                            || hasVibraniumShieldOutsideActiveSlot(utility, activeUtilitySlot)
                            || hasVibraniumShield(storage)
                            || hasVibraniumShield(backpack)
                            || hasVibraniumShield(tools);
            boolean useVibraniumBackShield =
                    resolveBackShieldTexturePreference(
                            hasNormalBackShieldCandidate,
                            hasVibraniumBackShieldCandidate,
                            backShieldPreference
                    );

            boolean shouldShowBackShield =
                    ShieldCapConfigManager.isBackShieldVisualEnabled()
                            && hasSpareShieldOutsideActiveHands;

            syncBackAttachment(player, shouldShowBackShield, useVibraniumBackShield, forceBackRebuild);

        } finally {
            syncingPlayers.remove(playerUuid);
        }
    }

    private boolean normalizeActiveSlotItem(ItemContainer container,
                                            byte activeSlot,
                                            String normalDesiredId,
                                            String vibraniumDesiredId) {
        if (container == null || activeSlot == Inventory.INACTIVE_SLOT_INDEX || activeSlot < 0 || activeSlot >= container.getCapacity()) {
            return false;
        }

        ItemStack current = container.getItemStack(activeSlot);
        if (!isShield(current)) {
            return false;
        }

        String desiredId = isVibraniumShield(current) ? vibraniumDesiredId : normalDesiredId;
        if (matchesId(current, desiredId)) {
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

            container.setItemStackForSlot(slot, remapItem(current, resolveBaseShieldId(current)));
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

            container.setItemStackForSlot(slot, remapItem(current, resolveBaseShieldId(current)));
            changed = true;
        }
        return changed;
    }

    private int countShields(ItemContainer container) {
        if (container == null) {
            return 0;
        }

        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (isShield(container.getItemStack(slot))) {
                count++;
            }
        }
        return count;
    }

    private int countShieldInEquippedSlot(ItemContainer container, byte activeSlot) {
        if (container == null || activeSlot == Inventory.INACTIVE_SLOT_INDEX || activeSlot < 0 || activeSlot >= container.getCapacity()) {
            return 0;
        }

        return isShield(container.getItemStack((short) activeSlot)) ? 1 : 0;
    }

    private void syncBackAttachment(Player player,
                                    boolean shouldShowBackShield,
                                    boolean useVibraniumBackShield,
                                    boolean forceBackRebuild) {
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
            newState.updateVibraniumBackShield(useVibraniumBackShield);
            if (shouldShowBackShield) {
                newState.updateShowBackShield(true);
                newState.setPendingModelReset(true);
            }
            if (forceBackRebuild) {
                if (shouldShowBackShield) {
                    newState.setPendingModelReset(true);
                }
                newState.rebuild();
            }
            store.putComponent(ref, ShieldCapBackStateComponent.getComponentType(), newState);
            return;
        }

        boolean changed = currentState.updateShowBackShield(shouldShowBackShield);
        boolean textureChanged = currentState.updateVibraniumBackShield(useVibraniumBackShield);
        if (shouldShowBackShield && (changed || textureChanged)) {
            currentState.setPendingModelReset(true);
        } else if (!shouldShowBackShield) {
            currentState.setPendingModelReset(false);
        }
        if (forceBackRebuild) {
            if (shouldShowBackShield) {
                currentState.setPendingModelReset(true);
            }
            currentState.rebuild();
        }

        if (changed || textureChanged || forceBackRebuild) {
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
                || matchesId(stack, LEFT_SHIELD_ID)
                || matchesId(stack, VIBRANIUM_BASE_SHIELD_ID)
                || matchesId(stack, VIBRANIUM_LEFT_SHIELD_ID);
    }

    private boolean isHandVariant(ItemStack stack) {
        return matchesId(stack, RIGHT_SHIELD_ID)
                || matchesId(stack, LEFT_SHIELD_ID)
                || matchesId(stack, VIBRANIUM_LEFT_SHIELD_ID);
    }

    private String resolveBaseShieldId(ItemStack stack) {
        return isVibraniumShield(stack) || isVibraniumThrownShield(stack)
                ? VIBRANIUM_BASE_SHIELD_ID
                : BASE_SHIELD_ID;
    }

    private boolean isVibraniumShield(ItemStack stack) {
        return matchesId(stack, VIBRANIUM_BASE_SHIELD_ID)
                || matchesId(stack, VIBRANIUM_LEFT_SHIELD_ID);
    }

    private boolean isNormalShield(ItemStack stack) {
        return matchesId(stack, BASE_SHIELD_ID)
                || matchesId(stack, RIGHT_SHIELD_ID)
                || matchesId(stack, LEFT_SHIELD_ID);
    }

    private boolean isVibraniumThrownShield(ItemStack stack) {
        if (!matchesId(stack, THROWN_SHIELD_ID)) {
            return false;
        }

        BsonDocument metadata = stack.getMetadata() != null ? stack.getMetadata() : null;
        if (metadata == null || !metadata.containsKey(VARIANT_METADATA_KEY)) {
            return false;
        }
        try {
            return VIBRANIUM_VARIANT_VALUE.equals(metadata.getString(VARIANT_METADATA_KEY, new BsonString("")).getValue());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasVibraniumShield(ItemContainer container) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (isVibraniumShield(container.getItemStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNormalShield(ItemContainer container) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (isNormalShield(container.getItemStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVibraniumShieldOutsideActiveSlot(ItemContainer container, byte activeSlot) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (slot == activeSlot) {
                continue;
            }
            if (isVibraniumShield(container.getItemStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNormalShieldOutsideActiveSlot(ItemContainer container, byte activeSlot) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (slot == activeSlot) {
                continue;
            }
            if (isNormalShield(container.getItemStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean resolveBackShieldTexturePreference(boolean hasNormalBackShieldCandidate,
                                                       boolean hasVibraniumBackShieldCandidate,
                                                       BackShieldPreference preference) {
        if (preference == BackShieldPreference.NORMAL) {
            return false;
        }
        if (preference == BackShieldPreference.VIBRANIUM) {
            return true;
        }
        if (hasNormalBackShieldCandidate) {
            return false;
        }
        return hasVibraniumBackShieldCandidate;
    }

    public enum BackShieldPreference {
        AUTO,
        AUTO_CLEAR_PENDING,
        NORMAL,
        VIBRANIUM
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
