package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapThrowKickRecentMarker extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowKickRecentMarker> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowKickRecentMarker.class,
                            ShieldCapThrowKickRecentMarker::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Marks a recent ShieldCap throw kick so the returning shield can relaunch as the throw-kick projectile.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (context.getCommandBuffer() == null) {
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null || uuidComponent.getUuid() == null) {
            return;
        }

        ShieldCapThrowHomingService.markRecentThrowKick(uuidComponent.getUuid());
    }
}
