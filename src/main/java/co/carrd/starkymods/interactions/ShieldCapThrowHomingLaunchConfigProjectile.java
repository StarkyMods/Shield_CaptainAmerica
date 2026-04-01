package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class ShieldCapThrowHomingLaunchConfigProjectile extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingLaunchConfigProjectile> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingLaunchConfigProjectile.class,
                            ShieldCapThrowHomingLaunchConfigProjectile::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("Config", Codec.STRING),
                            (o, i) -> o.config = i,
                            o -> o.config,
                            (o, p) -> o.config = p.config
                    )
                    .addValidator(ProjectileConfig.VALIDATOR_CACHE.getValidator().late())
                    .add()
                    .build();

    protected String config;

    @Nullable
    private ProjectileConfig getConfig() {
        return ProjectileConfig.getAssetMap().getAsset(config);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ownerRef = context.getEntity();
        if (commandBuffer == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        ProjectileConfig projectileConfig = getConfig();
        if (projectileConfig == null) {
            return;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(ownerRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }

        UUID ownerUuid = uuidComponent.getUuid();
        Transform look = TargetUtil.getLook(ownerRef, commandBuffer);
        Vector3d position = look.getPosition();
        Vector3d direction = look.getDirection();

        Ref<EntityStore> projectileRef =
                ProjectileModule.get().spawnProjectile(null, ownerRef, commandBuffer, projectileConfig, position, direction);
        if (projectileRef == null) {
            return;
        }

        ShieldCapThrowHomingService.markProjectile(ownerUuid, ownerRef, projectileRef);
    }
}
