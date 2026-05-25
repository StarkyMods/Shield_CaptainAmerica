package co.carrd.starkymods.interactions;

import co.carrd.starkymods.util.ShieldCapInventoryCompat;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

final class ShieldCapThrownHandResolver {
    static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";

    private ShieldCapThrownHandResolver() {
    }

    @Nonnull
    static ActiveThrownHand resolveActiveThrownHand(Store<EntityStore> store, Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return ActiveThrownHand.NONE;
        }

        Player player = store.getComponent(ownerRef, Player.getComponentType());
        if (player == null) {
            return ActiveThrownHand.NONE;
        }

        ItemContainer hotbar = ShieldCapInventoryCompat.getHotbar(store, ownerRef);
        byte activeHotbarSlot = ShieldCapInventoryCompat.getActiveHotbarSlot(store, ownerRef);
        if (isValidSlot(hotbar, activeHotbarSlot) && matchesId(hotbar.getItemStack(activeHotbarSlot), THROWN_ITEM_ID)) {
            return ActiveThrownHand.MAIN;
        }
        if (containsThrownShield(hotbar)) {
            return ActiveThrownHand.MAIN;
        }

        ItemContainer utility = ShieldCapInventoryCompat.getUtility(store, ownerRef);
        byte activeUtilitySlot = ShieldCapInventoryCompat.getActiveUtilitySlot(store, ownerRef);
        if (isValidSlot(utility, activeUtilitySlot) && matchesId(utility.getItemStack(activeUtilitySlot), THROWN_ITEM_ID)) {
            return ActiveThrownHand.LEFT;
        }

        if (containsThrownShield(utility)) {
            return ActiveThrownHand.LEFT;
        }

        return ActiveThrownHand.NONE;
    }

    private static boolean isValidSlot(ItemContainer container, byte slot) {
        return container != null
                && slot != InventoryComponent.INACTIVE_SLOT_INDEX
                && slot >= 0
                && slot < container.getCapacity();
    }

    private static boolean matchesId(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }

        String stackItemId = stack.getItemId();
        if (stackItemId == null || stackItemId.isBlank()) {
            return false;
        }

        return stackItemId.equals(itemId)
                || stackItemId.endsWith("." + itemId)
                || stackItemId.contains(itemId);
    }

    private static boolean containsThrownShield(ItemContainer container) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (matchesId(container.getItemStack(slot), THROWN_ITEM_ID)) {
                return true;
            }
        }

        return false;
    }

    enum ActiveThrownHand {
        NONE,
        MAIN,
        LEFT
    }
}
