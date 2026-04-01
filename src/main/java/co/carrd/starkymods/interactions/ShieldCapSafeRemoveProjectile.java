package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ShieldCapSafeRemoveProjectile extends SimpleInstantInteraction {
    private static final long REMOVE_DELAY_THROW_MS = 2000L;

    @Nonnull
    public static final BuilderCodec<ShieldCapSafeRemoveProjectile> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapSafeRemoveProjectile.class,
                            ShieldCapSafeRemoveProjectile::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("ForcedDelayMs", Codec.INTEGER),
                            (o, i) -> o.forcedDelayMs = i,
                            o -> o.forcedDelayMs,
                            (o, p) -> o.forcedDelayMs = p.forcedDelayMs
                    )
                    .add()
                    .documentation("Safely removes only projectile entities and never players.")
                    .build();

    protected int forcedDelayMs = -1;

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        Set<Ref<EntityStore>> candidates = new LinkedHashSet<>();
        addCandidate(candidates, context.getEntity());
        addCandidate(candidates, context.getOwningEntity());

        for (Ref<EntityStore> ref : candidates) {
            if (!isProjectileRef(commandBuffer, ref)) {
                continue;
            }

            UUID expectedUuid = getEntityUuid(commandBuffer, ref);
            long delayMs = forcedDelayMs >= 0 ? forcedDelayMs : REMOVE_DELAY_THROW_MS;
            scheduleSafeRemoval(commandBuffer, ref, expectedUuid, delayMs);
        }
    }

    private void addCandidate(@Nonnull Set<Ref<EntityStore>> candidates, Ref<EntityStore> ref) {
        if (ref != null && ref.isValid()) {
            candidates.add(ref);
        }
    }

    private boolean isProjectileRef(@Nonnull ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        try {
            if (accessor.getComponent(ref, Player.getComponentType()) != null) {
                return false;
            }

            return accessor.getComponent(ref, ProjectileComponent.getComponentType()) != null
                    || accessor.getComponent(ref, ProjectileModule.get().getProjectileComponentType()) != null
                    || accessor.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void scheduleSafeRemoval(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull Ref<EntityStore> ref,
                                     UUID expectedUuid,
                                     long delayMs) {
        Store<EntityStore> store = commandBuffer.getStore();
        if (store == null || store.getExternalData() == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        scheduleSafeRemoval(store, world, ref, expectedUuid, delayMs);
    }

    public static void scheduleSafeRemoval(@Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> ref,
                                           UUID expectedUuid,
                                           long delayMs) {
        if (store.getExternalData() == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        scheduleSafeRemoval(store, world, ref, expectedUuid, delayMs);
    }

    private static void scheduleSafeRemoval(@Nonnull Store<EntityStore> store,
                                            @Nonnull World world,
                                            @Nonnull Ref<EntityStore> ref,
                                            UUID expectedUuid,
                                            long delayMs) {
        if (delayMs <= 0L) {
            attemptStoreRemoval(store, ref, expectedUuid);
            scheduleRemovalAttempt(store, ref, expectedUuid, 50L, world);
            scheduleRemovalAttempt(store, ref, expectedUuid, 150L, world);
            return;
        }

        scheduleRemovalAttempt(store, ref, expectedUuid, delayMs, world);
        scheduleRemovalAttempt(store, ref, expectedUuid, delayMs + 100L, world);
        scheduleRemovalAttempt(store, ref, expectedUuid, delayMs + 300L, world);
        scheduleRemovalAttempt(store, ref, expectedUuid, delayMs + 600L, world);
        scheduleRemovalAttempt(store, ref, expectedUuid, delayMs + 1000L, world);
    }

    private static void scheduleRemovalAttempt(@Nonnull Store<EntityStore> store,
                                               @Nonnull Ref<EntityStore> ref,
                                               UUID expectedUuid,
                                               long delayMs,
                                               @Nonnull World world) {
        CompletableFuture<Void> timer = new CompletableFuture<>();
        timer.completeOnTimeout(null, Math.max(0L, delayMs), TimeUnit.MILLISECONDS)
                .thenRunAsync(() -> attemptStoreRemoval(store, ref, expectedUuid), world);
    }

    private static void attemptStoreRemoval(@Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref,
                                            UUID expectedUuid) {
        if (!ref.isValid()) {
            return;
        }

        if (store.getComponent(ref, Player.getComponentType()) != null) {
            return;
        }

        if (!isProjectileRefStatic(store, ref)) {
            return;
        }

        UUID currentUuid = getEntityUuidStatic(store, ref);
        if (expectedUuid != null && currentUuid != null && !expectedUuid.equals(currentUuid)) {
            return;
        }

        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    private UUID getEntityUuid(@Nonnull ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return getEntityUuidStatic(accessor, ref);
    }

    private static UUID getEntityUuidStatic(@Nonnull ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = accessor.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    private static boolean isProjectileRefStatic(@Nonnull ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        try {
            if (accessor.getComponent(ref, Player.getComponentType()) != null) {
                return false;
            }

            return accessor.getComponent(ref, ProjectileComponent.getComponentType()) != null
                    || accessor.getComponent(ref, ProjectileModule.get().getProjectileComponentType()) != null
                    || accessor.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
