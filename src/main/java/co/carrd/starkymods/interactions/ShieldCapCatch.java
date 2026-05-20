package co.carrd.starkymods.interactions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.bson.BsonDocument;
import org.bson.BsonString;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

public final class ShieldCapCatch extends SimpleInstantInteraction {
    private static final boolean DEBUG = false;
    private static final String LOG_PREFIX = "[ShieldCapCatchDebug] ";
    private static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String RETURNED_ITEM_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String VIBRANIUM_RETURNED_ITEM_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String CARTER_RETURNED_ITEM_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String GEORGIO_RETURNED_ITEM_ID = "Weapon_Shield_Georgio_Starky";
    private static final String ANTI_RETURNED_ITEM_ID = "Weapon_Shield_AntiCaptainAmerica_Starky";
    private static final String VARIANT_METADATA_KEY = "ShieldCapVariant";
    private static final String VIBRANIUM_VARIANT_VALUE = "Vibranium";
    private static final String CARTER_VARIANT_VALUE = "Carter";
    private static final String GEORGIO_VARIANT_VALUE = "Georgio";
    private static final String ANTI_VARIANT_VALUE = "AntiCaptainAmerica";
    private static final String CATCH_SOUND_ID = "SFX_ShieldCap_Catch";
    private static final long[] CALLING_ANIMATION_CLEAR_RETRY_DELAYS_MS =
            new long[] {0L, 100L};

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
        String restoredContainer = restoreInContainer(inventory.getHotbar(), "hotbar");
        if (restoredContainer == null) {
            restoredContainer = restoreInContainer(inventory.getUtility(), "utility");
        }
        if (restoredContainer == null) {
            restoredContainer = restoreInContainer(inventory.getStorage(), "storage");
        }
        if (restoredContainer == null) {
            restoredContainer = restoreInContainer(inventory.getBackpack(), "backpack");
        }
        if (restoredContainer == null) {
            restoredContainer = restoreInContainer(inventory.getTools(), "tools");
        }
        boolean restored = restoredContainer != null;
        debug("restoreToOwnerAndRemoveProjectile | owner=" + getEntityUuid(store, ownerRef)
                + " | projectile=" + getEntityUuid(store, projectileRef)
                + " | restored=" + restored
                + " | container=" + restoredContainer);

        if (!restored || projectileRef == null || !projectileRef.isValid()) {
            if (restored) {
                playCatchSound(store, ownerRef);
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
        playCatchSound(store, ownerRef);
        scheduleCallingAnimationClearPasses(store, ownerRef);
    }

    private static void playCatchSound(Store<EntityStore> store, Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid() || store.getExternalData() == null) {
            return;
        }

        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(CATCH_SOUND_ID, SoundEvent.EMPTY_ID);
        if (soundIndex == SoundEvent.EMPTY_ID) {
            return;
        }

        TransformComponent transform = store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        World world = store.getExternalData().getWorld();
        if (transform == null || transform.getPosition() == null || world == null) {
            return;
        }

        PlaySoundEvent3D packet = new PlaySoundEvent3D(
                soundIndex,
                SoundCategory.SFX,
                PositionUtil.toPositionPacket(transform.getPosition()),
                1.0f,
                1.0f
        );
        world.getNotificationHandler().sendPacketIfChunkLoaded(
                packet,
                (int) Math.floor(transform.getPosition().x),
                (int) Math.floor(transform.getPosition().z)
        );
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

    private static String restoreInContainer(ItemContainer container, String containerName) {
        if (container == null) {
            return null;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack current = container.getItemStack(slot);
            if (!matchesId(current, THROWN_ITEM_ID)) {
                continue;
            }

            container.setItemStackForSlot(slot, remapItem(current, resolveReturnedItemId(current)));
            return containerName + "[" + slot + "]";
        }

        return null;
    }

    private static String resolveReturnedItemId(ItemStack current) {
        String variant = getThrownVariant(current);
        if (VIBRANIUM_VARIANT_VALUE.equals(variant)) {
            return VIBRANIUM_RETURNED_ITEM_ID;
        }
        if (CARTER_VARIANT_VALUE.equals(variant)) {
            return CARTER_RETURNED_ITEM_ID;
        }
        if (GEORGIO_VARIANT_VALUE.equals(variant)) {
            return GEORGIO_RETURNED_ITEM_ID;
        }
        if (ANTI_VARIANT_VALUE.equals(variant)) {
            return ANTI_RETURNED_ITEM_ID;
        }
        return RETURNED_ITEM_ID;
    }

    private static String getThrownVariant(ItemStack current) {
        BsonDocument metadata = copyMetadata(current);
        if (metadata == null || !metadata.containsKey(VARIANT_METADATA_KEY)) {
            return null;
        }
        try {
            return metadata.getString(VARIANT_METADATA_KEY, new BsonString("")).getValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack remapItem(ItemStack current, String targetItemId) {
        BsonDocument copiedMetadata = copyMetadata(current);
        if (copiedMetadata != null) {
            copiedMetadata.remove(VARIANT_METADATA_KEY);
        }

        ItemStack newItem = new ItemStack(targetItemId, 1, copiedMetadata);
        double copiedDurability = current.getDurability();
        if (Double.isFinite(copiedDurability) && copiedDurability >= 0.0) {
            newItem = newItem.withDurability(copiedDurability);
        }
        return newItem;
    }

    private static BsonDocument copyMetadata(ItemStack current) {
        if (current == null) {
            return null;
        }

        String rawMetadata = current.toPacket().metadata;
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return null;
        }

        try {
            return BsonDocument.parse(rawMetadata);
        } catch (Exception ignored) {
            return null;
        }
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

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println(LOG_PREFIX + message);
        }
    }
}
