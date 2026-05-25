package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.BallisticData;
import com.hypixel.hytale.server.core.modules.projectile.config.BallisticDataProvider;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class ShieldCapThrowHomingLaunchProjectile extends SimpleInstantInteraction implements BallisticDataProvider {
    private static final double REF_RESOLVE_RANGE = 6.0;
    private static final double REF_RESOLVE_HEIGHT = 6.0;
    private static final double REF_RESOLVE_MIN_SPEED = 6.0;

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingLaunchProjectile> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingLaunchProjectile.class,
                            ShieldCapThrowHomingLaunchProjectile::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Launches a projectile and marks it for ShieldCap homing.")
                    .appendInherited(
                            new KeyedCodec<>("ProjectileId", Codec.STRING),
                            (o, i) -> o.projectileId = i,
                            o -> o.projectileId,
                            (o, p) -> o.projectileId = p.projectileId
                    )
                    .addValidator(Validators.nonNull())
                    .addValidator(ProjectileConfig.VALIDATOR_CACHE.getValidator().late())
                    .add()
                    .build();

    protected String projectileId;

    @Nonnull
    public String getProjectileId() {
        return projectileId;
    }

    @Override
    public BallisticData getBallisticData() {
        return ProjectileConfig.getAssetMap().getAsset(projectileId);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        Ref<EntityStore> entityRef = resolveOwnerRef(commandBuffer, context);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        Transform look = TargetUtil.getLook(entityRef, commandBuffer);
        Vector3d position = look.getPosition();
        Rotation3f rotation = look.getRotation();

        UUIDComponent uuidComponent = commandBuffer.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        UUID ownerUuid = uuidComponent.getUuid();

        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
        if (timeResource == null) {
            return;
        }

        Holder<EntityStore> projectileHolder =
                ProjectileComponent.assembleDefaultProjectile(timeResource, projectileId, position, rotation);
        ProjectileComponent projectileComponent =
                projectileHolder.getComponent(ProjectileComponent.getComponentType());
        if (projectileComponent == null) {
            return;
        }

        projectileHolder.ensureComponent(Intangible.getComponentType());

        if (projectileComponent.getProjectile() == null) {
            projectileComponent.initialize();
            if (projectileComponent.getProjectile() == null) {
                return;
            }
        }

        projectileComponent.shoot(
                projectileHolder,
                ownerUuid,
                position.x(),
                position.y(),
                position.z(),
                rotation.yaw(),
                rotation.pitch()
        );

        Ref<EntityStore> projectileRef = commandBuffer.addEntity(projectileHolder, AddReason.SPAWN);
        Ref<EntityStore> markRef = projectileRef;
        if (markRef == null || !markRef.isValid()) {
            markRef = resolveSpawnedProjectileRef(commandBuffer, entityRef, ownerUuid, projectileId);
        }

        boolean canMark = markRef != null
                && markRef.isValid()
                && commandBuffer.getComponent(markRef, ProjectileComponent.getComponentType()) != null
                && commandBuffer.getComponent(markRef, Player.getComponentType()) == null;
        if (canMark) {
            ShieldCapThrowHomingService.markProjectile(ownerUuid, entityRef, markRef);
        }
    }

    private Ref<EntityStore> resolveSpawnedProjectileRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                         @Nonnull Ref<EntityStore> ownerRef,
                                                         @Nonnull UUID ownerUuid,
                                                         @Nonnull String launchedProjectileId) {
        Store<EntityStore> store = commandBuffer.getStore();
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return null;
        }

        TransformComponent ownerTransform =
                commandBuffer.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        if (ownerTransform == null || ownerTransform.getPosition() == null) {
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
        spatial.getSpatialStructure().collectCylinder(ownerPos, REF_RESOLVE_RANGE, REF_RESOLVE_HEIGHT, refs);

        Ref<EntityStore> bestRef = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid() || ref.equals(ownerRef)) {
                continue;
            }

            ProjectileComponent projectileComponent =
                    commandBuffer.getComponent(ref, ProjectileComponent.getComponentType());
            if (projectileComponent == null) {
                continue;
            }
            if (!launchedProjectileId.equals(projectileComponent.getProjectileAssetName())) {
                continue;
            }

            StandardPhysicsProvider physicsProvider =
                    commandBuffer.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType());
            if (physicsProvider != null) {
                UUID creatorUuid = physicsProvider.getCreatorUuid();
                if (creatorUuid != null && !creatorUuid.equals(ownerUuid)) {
                    continue;
                }
            }

            Velocity velocity = commandBuffer.getComponent(ref, EntityModule.get().getVelocityComponentType());
            if (velocity == null || velocity.getVelocity() == null || velocity.getVelocity().length() < REF_RESOLVE_MIN_SPEED) {
                continue;
            }

            TransformComponent transform = commandBuffer.getComponent(ref, EntityModule.get().getTransformComponentType());
            if (transform == null || transform.getPosition() == null) {
                continue;
            }

            double distanceSq = ownerPos.distanceSquared(transform.getPosition());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestRef = ref;
            }
        }

        refs.clear();
        return bestRef;
    }

    private Ref<EntityStore> resolveOwnerRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                             @Nonnull InteractionContext context) {
        Ref<EntityStore> entityRef = context.getEntity();
        if (hasPlayer(commandBuffer, entityRef)) {
            return entityRef;
        }

        Ref<EntityStore> owningRef = context.getOwningEntity();
        if (hasPlayer(commandBuffer, owningRef)) {
            return owningRef;
        }

        return null;
    }

    private boolean hasPlayer(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                              Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        return commandBuffer.getComponent(ref, Player.getComponentType()) != null;
    }
}
