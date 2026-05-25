package co.carrd.starkymods.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapInventoryCompat {
    private ShieldCapInventoryCompat() {
    }

    @Nullable
    public static ItemContainer getHotbar(@Nullable ComponentAccessor<EntityStore> accessor,
                                          @Nullable Ref<EntityStore> playerRef) {
        return getSection(accessor, playerRef, InventoryComponent.HOTBAR_SECTION_ID);
    }

    @Nullable
    public static ItemContainer getUtility(@Nullable ComponentAccessor<EntityStore> accessor,
                                           @Nullable Ref<EntityStore> playerRef) {
        return getSection(accessor, playerRef, InventoryComponent.UTILITY_SECTION_ID);
    }

    @Nullable
    public static ItemContainer getCombinedEverything(@Nullable ComponentAccessor<EntityStore> accessor,
                                                      @Nullable Ref<EntityStore> playerRef) {
        ItemContainer[] containers = getAllContainers(accessor, playerRef);
        if (containers.length == 0) {
            return null;
        }
        if (containers.length == 1) {
            return containers[0];
        }
        return new CombinedItemContainer(containers);
    }

    public static ItemContainer[] getAllContainers(@Nullable ComponentAccessor<EntityStore> accessor,
                                                   @Nullable Ref<EntityStore> playerRef) {
        if (!isUsable(accessor, playerRef)) {
            return new ItemContainer[0];
        }

        List<ItemContainer> containers = new ArrayList<>(6);
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.HOTBAR_SECTION_ID));
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.STORAGE_SECTION_ID));
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.BACKPACK_SECTION_ID));
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.UTILITY_SECTION_ID));
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.TOOLS_SECTION_ID));
        addIfPresent(containers, getSection(accessor, playerRef, InventoryComponent.ARMOR_SECTION_ID));
        return containers.toArray(ItemContainer[]::new);
    }

    @Nullable
    public static ItemStack getItemInHand(@Nullable ComponentAccessor<EntityStore> accessor,
                                          @Nullable Ref<EntityStore> playerRef) {
        if (!isUsable(accessor, playerRef)) {
            return null;
        }
        try {
            return InventoryComponent.getItemInHand(accessor, playerRef);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static ItemStack getUtilityItem(@Nullable ComponentAccessor<EntityStore> accessor,
                                           @Nullable Ref<EntityStore> playerRef) {
        ItemContainer utility = getUtility(accessor, playerRef);
        byte slot = getActiveUtilitySlot(accessor, playerRef);
        if (utility == null || slot < 0 || slot >= utility.getCapacity()) {
            return null;
        }
        return utility.getItemStack(slot);
    }

    public static byte getActiveHotbarSlot(@Nullable ComponentAccessor<EntityStore> accessor,
                                           @Nullable Ref<EntityStore> playerRef) {
        return getActiveSlot(accessor, playerRef, InventoryComponent.HOTBAR_SECTION_ID);
    }

    public static byte getActiveUtilitySlot(@Nullable ComponentAccessor<EntityStore> accessor,
                                            @Nullable Ref<EntityStore> playerRef) {
        return getActiveSlot(accessor, playerRef, InventoryComponent.UTILITY_SECTION_ID);
    }

    public static void setActiveHotbarSlot(@Nullable ComponentAccessor<EntityStore> accessor,
                                           @Nullable Ref<EntityStore> playerRef,
                                           byte slot) {
        setActiveSlot(accessor, playerRef, InventoryComponent.HOTBAR_SECTION_ID, slot);
    }

    @Nullable
    public static ItemContainer getSection(@Nullable ComponentAccessor<EntityStore> accessor,
                                           @Nullable Ref<EntityStore> playerRef,
                                           int sectionId) {
        if (!isUsable(accessor, playerRef)) {
            return null;
        }
        try {
            InventoryComponent component = getInventoryComponent(accessor, playerRef, sectionId);
            return component == null ? null : component.getInventory();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static byte getActiveSlot(@Nullable ComponentAccessor<EntityStore> accessor,
                                      @Nullable Ref<EntityStore> playerRef,
                                      int sectionId) {
        if (!isUsable(accessor, playerRef)) {
            return InventoryComponent.INACTIVE_SLOT_INDEX;
        }
        try {
            ActiveSlotInventoryComponent component = getActiveSlotInventoryComponent(accessor, playerRef, sectionId);
            return component == null ? InventoryComponent.INACTIVE_SLOT_INDEX : component.getActiveSlot();
        } catch (RuntimeException ignored) {
            return InventoryComponent.INACTIVE_SLOT_INDEX;
        }
    }

    private static void setActiveSlot(@Nullable ComponentAccessor<EntityStore> accessor,
                                      @Nullable Ref<EntityStore> playerRef,
                                      int sectionId,
                                      byte slot) {
        if (!isUsable(accessor, playerRef)) {
            return;
        }
        try {
            ActiveSlotInventoryComponent component = getActiveSlotInventoryComponent(accessor, playerRef, sectionId);
            if (component != null) {
                component.setActiveSlot(slot, playerRef, accessor);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private static InventoryComponent getInventoryComponent(@Nullable ComponentAccessor<EntityStore> accessor,
                                                            @Nullable Ref<EntityStore> playerRef,
                                                            int sectionId) {
        if (!isUsable(accessor, playerRef)) {
            return null;
        }
        ComponentType<EntityStore, ? extends InventoryComponent> componentType =
                InventoryComponent.getComponentTypeById(sectionId);
        if (componentType == null) {
            return null;
        }
        return accessor.getComponent(playerRef, componentType);
    }

    @Nullable
    private static ActiveSlotInventoryComponent getActiveSlotInventoryComponent(
            @Nullable ComponentAccessor<EntityStore> accessor,
            @Nullable Ref<EntityStore> playerRef,
            int sectionId) {
        InventoryComponent component = getInventoryComponent(accessor, playerRef, sectionId);
        return component instanceof ActiveSlotInventoryComponent activeSlotComponent
                ? activeSlotComponent
                : null;
    }

    private static boolean isUsable(@Nullable ComponentAccessor<EntityStore> accessor,
                                    @Nullable Ref<EntityStore> playerRef) {
        return accessor != null && playerRef != null && playerRef.isValid();
    }

    private static void addIfPresent(List<ItemContainer> containers, ItemContainer container) {
        if (container != null) {
            containers.add(container);
        }
    }
}
