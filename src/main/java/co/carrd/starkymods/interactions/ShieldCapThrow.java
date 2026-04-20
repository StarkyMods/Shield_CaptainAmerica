package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class ShieldCapThrow extends SimpleInstantInteraction {
    private static final String MAIN_HAND_ITEM_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String LEFT_HAND_ITEM_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig";
    private static final double DURABILITY_COST = 0.21d;

    @Nonnull
    public static final BuilderCodec<ShieldCapThrow> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrow.class,
                            ShieldCapThrow::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (!swapHeldShieldItem(context, THROWN_ITEM_ID)) {
            markFailed(context);
            return;
        }
        if (!launchProjectile(context, PROJECTILE_CONFIG_ID)) {
            markFailed(context);
        }
    }

    private boolean swapHeldShieldItem(@Nonnull InteractionContext context, @Nonnull String targetItemId) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            return false;
        }

        Ref<EntityStore> ref = context.getEntity();
        if (ref == null || !ref.isValid()) {
            return false;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        if (!(component instanceof Player player)) {
            return false;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        byte activeHotbarSlot = inventory.getActiveHotbarSlot();
        ItemContainer hotbarContainer = inventory.getHotbar();
        if (isValidSlot(hotbarContainer, activeHotbarSlot)) {
            ItemStack mainHandStack = hotbarContainer.getItemStack(activeHotbarSlot);
            if (matchesId(mainHandStack, MAIN_HAND_ITEM_ID)) {
                return replaceItemInSlot(hotbarContainer, activeHotbarSlot, mainHandStack, targetItemId, DURABILITY_COST);
            }
        }

        byte activeUtilitySlot = inventory.getActiveUtilitySlot();
        ItemContainer utilityContainer = inventory.getUtility();
        if (isValidSlot(utilityContainer, activeUtilitySlot)) {
            ItemStack leftHandStack = utilityContainer.getItemStack(activeUtilitySlot);
            if (matchesId(leftHandStack, LEFT_HAND_ITEM_ID)) {
                return replaceItemInSlot(utilityContainer, activeUtilitySlot, leftHandStack, targetItemId, DURABILITY_COST);
            }
        }

        return false;
    }

    private boolean replaceItemInSlot(@Nonnull ItemContainer container,
                                      byte slot,
                                      @Nonnull ItemStack sourceItem,
                                      @Nonnull String targetItemId,
                                      double durabilityCost) {
        if (sourceItem.isEmpty()) {
            return false;
        }

        String rawMetadata = sourceItem.toPacket().metadata;
        BsonDocument copiedMetadata = null;
        if (rawMetadata != null && !rawMetadata.isBlank()) {
            try {
                copiedMetadata = BsonDocument.parse(rawMetadata);
            } catch (Exception ignored) {
                copiedMetadata = null;
            }
        }

        ItemStack newItem = new ItemStack(targetItemId, 1, copiedMetadata);

        double copiedDurability = sourceItem.getDurability();
        if (Double.isFinite(copiedDurability) && copiedDurability >= 0.0) {
            double adjustedDurability = Math.max(0.0d, copiedDurability - Math.max(0.0d, durabilityCost));
            newItem = newItem.withDurability(adjustedDurability);
        }

        container.setItemStackForSlot(slot, newItem);
        return true;
    }

    private boolean launchProjectile(@Nonnull InteractionContext context, @Nonnull String projectileConfigId) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ownerRef = context.getEntity();
        if (commandBuffer == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        ProjectileConfig projectileConfig = getProjectileConfig(projectileConfigId);
        if (projectileConfig == null) {
            return false;
        }

        UUID ownerUuid = getOwnerUuid(commandBuffer, ownerRef);
        if (ownerUuid == null) {
            return false;
        }

        Transform look = TargetUtil.getLook(ownerRef, commandBuffer);
        Vector3d position = look.getPosition();
        Vector3d direction = look.getDirection();

        Ref<EntityStore> projectileRef =
                ProjectileModule.get().spawnProjectile(null, ownerRef, commandBuffer, projectileConfig, position, direction);
        if (projectileRef == null || !projectileRef.isValid()) {
            return false;
        }

        ShieldCapThrowHomingService.markProjectile(ownerUuid, ownerRef, projectileRef);
        return true;
    }

    @Nullable
    private ProjectileConfig getProjectileConfig(@Nonnull String projectileConfigId) {
        return ProjectileConfig.getAssetMap().getAsset(projectileConfigId);
    }

    @Nullable
    private UUID getOwnerUuid(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Ref<EntityStore> ownerRef) {
        UUIDComponent uuidComponent = commandBuffer.getComponent(ownerRef, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private boolean isValidSlot(ItemContainer container, byte slot) {
        return container != null
                && slot != Inventory.INACTIVE_SLOT_INDEX
                && slot >= 0
                && slot < container.getCapacity();
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

    private void markFailed(@Nonnull InteractionContext context) {
        if (context.getState() != null) {
            context.getState().state = InteractionState.Failed;
        }
        if (context.getClientState() != null) {
            context.getClientState().state = InteractionState.Failed;
        }
        if (context.getServerState() != null) {
            context.getServerState().state = InteractionState.Failed;
        }
    }
}
