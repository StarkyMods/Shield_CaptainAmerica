package co.carrd.starkymods.visuals;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.SwitchActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapUtilityQuickSwapSystem extends EntityEventSystem<EntityStore, SwitchActiveSlotEvent> {
    private static final String NORMAL_BASE_SHIELD_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String NORMAL_LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String VIBRANIUM_BASE_SHIELD_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String CARTER_BASE_SHIELD_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String CARTER_LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainCarter_Starky";
    private static final String GEORGIO_BASE_SHIELD_ID = "Weapon_Shield_Georgio_Starky";
    private static final String GEORGIO_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Georgio_Starky";

    public ShieldCapUtilityQuickSwapSystem() {
        super(SwitchActiveSlotEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       SwitchActiveSlotEvent event) {
        if (event == null || event.getInventorySectionId() != Inventory.UTILITY_SECTION_ID) {
            return;
        }

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        Player player = chunk.getComponent(index, Player.getComponentType());
        Inventory inventory = player != null ? player.getInventory() : null;
        ItemContainer utility = inventory != null ? inventory.getUtility() : null;
        if (utility == null) {
            return;
        }

        String previousItemId = itemIdAt(utility, event.getPreviousSlot());
        String currentItemId = itemIdAt(utility, event.getNewSlot());

        ShieldCapVisualSyncService.BackShieldPreference previousVariant = variantPreference(previousItemId);
        ShieldCapVisualSyncService.BackShieldPreference currentVariant = variantPreference(currentItemId);
        ShieldCapVisualSyncService.BackShieldPreference preference = resolvePreference(previousVariant, currentVariant);
        if (preference == null) {
            return;
        }

        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin != null) {
            plugin.getVisualSyncService().syncDeferred(player, false, preference, null, Integer.valueOf(event.getNewSlot()));
        }
    }

    private String itemIdAt(ItemContainer container, int slot) {
        if (container == null || slot == Inventory.INACTIVE_SLOT_INDEX || slot < 0 || slot >= container.getCapacity()) {
            return "";
        }

        ItemStack stack = container.getItemStack((short) slot);
        return stack != null && !stack.isEmpty() && stack.getItemId() != null ? stack.getItemId() : "";
    }

    private ShieldCapVisualSyncService.BackShieldPreference resolvePreference(
            ShieldCapVisualSyncService.BackShieldPreference previousVariant,
            ShieldCapVisualSyncService.BackShieldPreference currentVariant) {
        if (previousVariant != null) {
            return previousVariant;
        }
        if (currentVariant != null) {
            return ShieldCapVisualSyncService.BackShieldPreference.AUTO_CLEAR_PENDING;
        }
        return null;
    }

    private ShieldCapVisualSyncService.BackShieldPreference variantPreference(String itemId) {
        if (matchesId(itemId, NORMAL_BASE_SHIELD_ID) || matchesId(itemId, NORMAL_LEFT_SHIELD_ID)) {
            return ShieldCapVisualSyncService.BackShieldPreference.NORMAL;
        }
        if (matchesId(itemId, VIBRANIUM_BASE_SHIELD_ID) || matchesId(itemId, VIBRANIUM_LEFT_SHIELD_ID)) {
            return ShieldCapVisualSyncService.BackShieldPreference.VIBRANIUM;
        }
        if (matchesId(itemId, CARTER_BASE_SHIELD_ID) || matchesId(itemId, CARTER_LEFT_SHIELD_ID)) {
            return ShieldCapVisualSyncService.BackShieldPreference.CARTER;
        }
        if (matchesId(itemId, GEORGIO_BASE_SHIELD_ID) || matchesId(itemId, GEORGIO_LEFT_SHIELD_ID)) {
            return ShieldCapVisualSyncService.BackShieldPreference.GEORGIO;
        }
        return null;
    }

    private boolean matchesId(String itemId, String baseId) {
        if (itemId == null || itemId.isBlank() || baseId == null || baseId.isBlank()) {
            return false;
        }
        return itemId.equals(baseId)
                || itemId.endsWith("." + baseId)
                || itemId.contains(baseId);
    }
}
