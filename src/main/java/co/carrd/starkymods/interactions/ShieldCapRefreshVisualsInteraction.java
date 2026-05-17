package co.carrd.starkymods.interactions;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import co.carrd.starkymods.visuals.ShieldCapVisualSyncService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ShieldCapRefreshVisualsInteraction extends SimpleInstantInteraction {
    private static final String MAIN_SHIELD_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String VIBRANIUM_MAIN_SHIELD_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String CARTER_MAIN_SHIELD_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String CARTER_LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainCarter_Starky";
    private static final String GEORGIO_MAIN_SHIELD_ID = "Weapon_Shield_Georgio_Starky";
    private static final String GEORGIO_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Georgio_Starky";

    @Nonnull
    public static final BuilderCodec<ShieldCapRefreshVisualsInteraction> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapRefreshVisualsInteraction.class,
                            ShieldCapRefreshVisualsInteraction::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("RefreshMode", Codec.STRING),
                            (interaction, value) -> interaction.refreshMode = value,
                            interaction -> interaction.refreshMode,
                            (interaction, parent) -> interaction.refreshMode = parent.refreshMode
                    )
                    .add()
                    .documentation("")
                    .build();

    private String refreshMode = "Auto";

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin == null) {
            return;
        }

        Player player = resolvePlayer(context.getCommandBuffer(), context);
        if (player == null) {
            return;
        }

        ShieldCapVisualSyncService.BackShieldPreference preference =
                isSwapFromRefresh()
                        ? resolveBackShieldPreference(player)
                        : ShieldCapVisualSyncService.BackShieldPreference.AUTO_CLEAR_PENDING;
        plugin.getVisualSyncService().syncDeferred(player, false, preference);
    }

    private boolean isSwapFromRefresh() {
        return refreshMode != null
                && (refreshMode.toLowerCase().startsWith("swapfrom")
                || "FromHand".equalsIgnoreCase(refreshMode));
    }

    private ShieldCapVisualSyncService.BackShieldPreference resolveBackShieldPreference(Player player) {
        if (player == null || player.getInventory() == null) {
            return ShieldCapVisualSyncService.BackShieldPreference.AUTO;
        }
        if (refreshMode != null) {
            if (refreshMode.toLowerCase().contains("normal")) {
                return ShieldCapVisualSyncService.BackShieldPreference.NORMAL;
            }
            if (refreshMode.toLowerCase().contains("vibranium")) {
                return ShieldCapVisualSyncService.BackShieldPreference.VIBRANIUM;
            }
            if (refreshMode.toLowerCase().contains("carter")) {
                return ShieldCapVisualSyncService.BackShieldPreference.CARTER;
            }
            if (refreshMode.toLowerCase().contains("georgio")) {
                return ShieldCapVisualSyncService.BackShieldPreference.GEORGIO;
            }
        }

        Inventory inventory = player.getInventory();
        ItemStack activeMain = getActiveStack(inventory.getHotbar(), inventory.getActiveHotbarSlot());
        ItemStack activeLeft = getActiveStack(inventory.getUtility(), inventory.getActiveUtilitySlot());

        if (isNormalShield(activeMain) || isNormalShield(activeLeft)) {
            return ShieldCapVisualSyncService.BackShieldPreference.NORMAL;
        }
        if (isVibraniumShield(activeMain) || isVibraniumShield(activeLeft)) {
            return ShieldCapVisualSyncService.BackShieldPreference.VIBRANIUM;
        }
        if (isCarterShield(activeMain) || isCarterShield(activeLeft)) {
            return ShieldCapVisualSyncService.BackShieldPreference.CARTER;
        }
        if (isGeorgioShield(activeMain) || isGeorgioShield(activeLeft)) {
            return ShieldCapVisualSyncService.BackShieldPreference.GEORGIO;
        }
        return ShieldCapVisualSyncService.BackShieldPreference.AUTO;
    }

    private ItemStack getActiveStack(ItemContainer container, byte slot) {
        if (container == null
                || slot == Inventory.INACTIVE_SLOT_INDEX
                || slot < 0
                || slot >= container.getCapacity()) {
            return null;
        }
        return container.getItemStack(slot);
    }

    private boolean isNormalShield(ItemStack stack) {
        return matchesId(stack, MAIN_SHIELD_ID) || matchesId(stack, LEFT_SHIELD_ID);
    }

    private boolean isVibraniumShield(ItemStack stack) {
        return matchesId(stack, VIBRANIUM_MAIN_SHIELD_ID) || matchesId(stack, VIBRANIUM_LEFT_SHIELD_ID);
    }

    private boolean isCarterShield(ItemStack stack) {
        return matchesId(stack, CARTER_MAIN_SHIELD_ID) || matchesId(stack, CARTER_LEFT_SHIELD_ID);
    }

    private boolean isGeorgioShield(ItemStack stack) {
        return matchesId(stack, GEORGIO_MAIN_SHIELD_ID) || matchesId(stack, GEORGIO_LEFT_SHIELD_ID);
    }

    private boolean matchesId(ItemStack stack, String itemId) {
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

    private Player resolvePlayer(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                 @Nonnull InteractionContext context) {
        Player player = getPlayerFromRef(commandBuffer, context.getEntity());
        if (player != null) {
            return player;
        }

        return getPlayerFromRef(commandBuffer, context.getOwningEntity());
    }

    private Player getPlayerFromRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        if (component instanceof Player player) {
            return player;
        }

        return null;
    }
}
