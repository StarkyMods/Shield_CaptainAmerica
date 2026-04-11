package co.carrd.starkymods.interactions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.bson.BsonDocument;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapCatch extends SimpleInstantInteraction {
    private static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String RETURNED_ITEM_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final long[] CALLING_ANIMATION_CLEAR_RETRY_DELAYS_MS = new long[] {500L, 650L, 800L};

    @Nonnull
    public static final BuilderCodec<ShieldCapCatch> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapCatch.class,
                            ShieldCapCatch::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Restores the thrown shield item back to the main shield variant.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Store<EntityStore> store = context.getCommandBuffer() == null ? null : context.getCommandBuffer().getStore();
        restoreToOwnerAndRemoveProjectile(
                store,
                context.getEntity(),
                resolveProjectileRef(store, context.getEntity(), context.getOwningEntity())
        );
    }

    public static void restoreToOwner(Store<EntityStore> store, Ref<EntityStore> ownerRef) {
        restoreToOwnerAndRemoveProjectile(store, ownerRef, null);
    }

    public static void restoreToOwnerAndRemoveProjectile(Store<EntityStore> store,
                                                         Ref<EntityStore> ownerRef,
                                                         Ref<EntityStore> projectileRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        Player player = store.getComponent(ownerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        boolean restored = restoreInContainer(inventory.getHotbar());
        if (!restored) {
            restored = restoreInContainer(inventory.getUtility());
        }
        if (!restored) {
            restored = restoreInContainer(inventory.getStorage());
        }
        if (!restored) {
            restored = restoreInContainer(inventory.getBackpack());
        }
        if (!restored) {
            restored = restoreInContainer(inventory.getTools());
        }

        if (!restored || projectileRef == null || !projectileRef.isValid()) {
            if (restored) {
                scheduleCallingAnimationClearPasses(store, ownerRef);
            }
            return;
        }

        UUIDComponent uuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
        ShieldCapSafeRemoveProjectile.scheduleSafeRemoval(
                store,
                projectileRef,
                uuidComponent == null ? null : uuidComponent.getUuid(),
                0L
        );
        scheduleCallingAnimationClearPasses(store, ownerRef);
    }

    private static void scheduleCallingAnimationClearPasses(Store<EntityStore> store,
                                                            Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()
                || store.getExternalData() == null || store.getExternalData().getWorld() == null) {
            return;
        }

        UUID ownerUuid = getEntityUuid(store, ownerRef);
        World world = store.getExternalData().getWorld();
        if (ownerUuid == null || world == null) {
            return;
        }

        for (long delayMs : CALLING_ANIMATION_CLEAR_RETRY_DELAYS_MS) {
            scheduleCallingAnimationClear(ownerUuid, world, delayMs);
        }
    }

    private static void scheduleCallingAnimationClear(UUID ownerUuid,
                                                      World world,
                                                      long delayMs) {
        if (ownerUuid == null || world == null) {
            return;
        }

        Runnable task = () -> {
            if (!world.isAlive()) {
                return;
            }

            ShieldCapThrowHomingService.queueReturnCallingAnimationClear(ownerUuid);
        };

        CompletableFuture<Void> timer = new CompletableFuture<>();
        timer.completeOnTimeout(null, delayMs, TimeUnit.MILLISECONDS)
                .thenRunAsync(task, world);
    }

    private static UUID getEntityUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private static boolean restoreInContainer(ItemContainer container) {
        if (container == null) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack current = container.getItemStack(slot);
            if (!matchesId(current, THROWN_ITEM_ID)) {
                continue;
            }

            container.setItemStackForSlot(slot, remapItem(current, RETURNED_ITEM_ID));
            return true;
        }

        return false;
    }

    private static ItemStack remapItem(ItemStack current, String targetItemId) {
        String rawMetadata = current.toPacket().metadata;
        BsonDocument copiedMetadata = null;
        if (rawMetadata != null && !rawMetadata.isBlank()) {
            try {
                copiedMetadata = BsonDocument.parse(rawMetadata);
            } catch (Exception ignored) {
                copiedMetadata = null;
            }
        }

        ItemStack newItem = new ItemStack(targetItemId, 1, copiedMetadata);
        double copiedDurability = current.getDurability();
        if (Double.isFinite(copiedDurability) && copiedDurability >= 0.0) {
            newItem = newItem.withDurability(copiedDurability);
        }
        return newItem;
    }

    private static boolean matchesId(ItemStack stack, String itemId) {
        return stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId());
    }

    private static Ref<EntityStore> resolveProjectileRef(Store<EntityStore> store,
                                                         Ref<EntityStore> entityRef,
                                                         Ref<EntityStore> owningRef) {
        if (isProjectileRef(store, entityRef)) {
            return entityRef;
        }
        if (isProjectileRef(store, owningRef)) {
            return owningRef;
        }
        return null;
    }

    private static boolean isProjectileRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }

        if (store.getComponent(ref, Player.getComponentType()) != null) {
            return false;
        }

        return store.getComponent(ref, ProjectileComponent.getComponentType()) != null
                || store.getComponent(ref, ProjectileModule.get().getProjectileComponentType()) != null
                || store.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType()) != null;
    }
}
