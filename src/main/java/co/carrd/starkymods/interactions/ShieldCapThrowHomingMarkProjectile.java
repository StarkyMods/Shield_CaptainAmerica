package co.carrd.starkymods.interactions;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ShieldCapThrowHomingMarkProjectile extends SimpleInstantInteraction {
    private static final double FALLBACK_SCAN_RANGE = 6.0;
    private static final double FALLBACK_SCAN_HEIGHT = 18.0;
    private static final double FALLBACK_MIN_SPEED = 8.0;

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingMarkProjectile> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingMarkProjectile.class,
                            ShieldCapThrowHomingMarkProjectile::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        ResolvedOwner owner = resolveOwner(commandBuffer, context);
        if (owner == null || owner.ownerUuid == null) {
            return;
        }

        Ref<EntityStore> projectileRef = resolveProjectileRef(commandBuffer, context, owner.ownerRef, owner.ownerUuid);
        if (projectileRef == null || !projectileRef.isValid()) {
            ShieldCapThrowHomingService.tripSafetyFuse(owner.ownerUuid, "mark interaction could not resolve projectile");
            return;
        }

        ShieldCapThrowHomingService.markProjectile(owner.ownerUuid, owner.ownerRef, projectileRef);
    }

    private ResolvedOwner resolveOwner(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                       @Nonnull InteractionContext context) {
        Ref<EntityStore> owningRef = context.getOwningEntity();
        Player owningPlayer = getPlayerFromRef(commandBuffer, owningRef);
        if (owningPlayer != null) {
            return new ResolvedOwner(((CommandSender) owningPlayer).getUuid(), owningRef);
        }

        Ref<EntityStore> entityRef = context.getEntity();
        Player entityPlayer = getPlayerFromRef(commandBuffer, entityRef);
        if (entityPlayer != null) {
            return new ResolvedOwner(((CommandSender) entityPlayer).getUuid(), entityRef);
        }

        return null;
    }

    private Ref<EntityStore> resolveProjectileRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                  @Nonnull InteractionContext context,
                                                  Ref<EntityStore> ownerRef,
                                                  UUID ownerUuid) {
        Ref<EntityStore> entityRef = context.getEntity();
        if (isConfigProjectile(commandBuffer, entityRef)
                && isOwnedByOwner(commandBuffer, entityRef, ownerUuid)
                && isLikelyProjectile(commandBuffer, entityRef, ownerRef)) {
            return entityRef;
        }

        Ref<EntityStore> owningRef = context.getOwningEntity();
        if (isConfigProjectile(commandBuffer, owningRef)
                && isOwnedByOwner(commandBuffer, owningRef, ownerUuid)
                && isLikelyProjectile(commandBuffer, owningRef, ownerRef)) {
            return owningRef;
        }

        Ref<EntityStore> nearbyRef = findNearbyProjectileForOwner(commandBuffer, ownerRef, ownerUuid, true);
        if (nearbyRef != null) {
            return nearbyRef;
        }

        return findNearbyProjectileForOwner(commandBuffer, ownerRef, ownerUuid, false);
    }

    private Ref<EntityStore> findNearbyProjectileForOwner(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                          Ref<EntityStore> ownerRef,
                                                          UUID ownerUuid,
                                                          boolean configOnly) {
        if (ownerRef == null || !ownerRef.isValid()) {
            return null;
        }

        Object ownerTransformObj = commandBuffer.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        if (!(ownerTransformObj instanceof TransformComponent ownerTransform) || ownerTransform.getPosition() == null) {
            return null;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        if (store == null) {
            return null;
        }

        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                store.getResource(EntityModule.get().getEntitySpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return null;
        }

        Vector3d ownerPos = ownerTransform.getPosition();
        List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
        refs.clear();
        spatial.getSpatialStructure().collectCylinder(ownerPos, FALLBACK_SCAN_RANGE, FALLBACK_SCAN_HEIGHT, refs);

        Ref<EntityStore> bestRef = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Ref<EntityStore> ref : refs) {
            if (configOnly && !isConfigProjectile(commandBuffer, ref)) {
                continue;
            }
            if (!isOwnedByOwner(commandBuffer, ref, ownerUuid)) {
                continue;
            }
            if (!isLikelyProjectile(commandBuffer, ref, ownerRef)) {
                continue;
            }

            Object velocityObj = commandBuffer.getComponent(ref, EntityModule.get().getVelocityComponentType());
            if (!(velocityObj instanceof Velocity velocity)) {
                continue;
            }

            Vector3d velocityVec = velocity.getVelocity();
            double speed = velocityVec != null ? velocityVec.length() : 0.0;
            if (speed < FALLBACK_MIN_SPEED) {
                continue;
            }

            Object transformObj = commandBuffer.getComponent(ref, EntityModule.get().getTransformComponentType());
            if (!(transformObj instanceof TransformComponent transform) || transform.getPosition() == null) {
                continue;
            }

            double distanceSq = ownerPos.distanceSquaredTo(transform.getPosition());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestRef = ref;
            }
        }

        refs.clear();
        return bestRef;
    }

    private boolean isConfigProjectile(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                       Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        StandardPhysicsProvider provider =
                commandBuffer.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        return provider != null;
    }

    private boolean isOwnedByOwner(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                   Ref<EntityStore> ref,
                                   UUID ownerUuid) {
        if (ownerUuid == null || ref == null || !ref.isValid()) {
            return false;
        }

        StandardPhysicsProvider provider =
                commandBuffer.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (provider == null) {
            return false;
        }

        UUID creatorUuid = provider.getCreatorUuid();
        return creatorUuid != null && creatorUuid.equals(ownerUuid);
    }

    private boolean isLikelyProjectile(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                       Ref<EntityStore> ref,
                                       Ref<EntityStore> ownerRef) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(ref)) {
            return false;
        }

        Object maybePlayer = commandBuffer.getComponent(ref, Player.getComponentType());
        if (maybePlayer instanceof Player) {
            return false;
        }

        boolean hasProjectileMarker =
                commandBuffer.getComponent(ref, ProjectileModule.get().getProjectileComponentType()) != null
                        || commandBuffer.getComponent(ref, ProjectileComponent.getComponentType()) != null
                        || commandBuffer.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType()) != null;
        if (!hasProjectileMarker) {
            return false;
        }

        Object transform = commandBuffer.getComponent(ref, EntityModule.get().getTransformComponentType());
        Object velocity = commandBuffer.getComponent(ref, EntityModule.get().getVelocityComponentType());
        return transform != null && velocity instanceof Velocity;
    }

    private Player getPlayerFromRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        return component instanceof Player player ? player : null;
    }

    private static final class ResolvedOwner {
        private final UUID ownerUuid;
        private final Ref<EntityStore> ownerRef;

        private ResolvedOwner(UUID ownerUuid, Ref<EntityStore> ownerRef) {
            this.ownerUuid = ownerUuid;
            this.ownerRef = ownerRef;
        }
    }
}
