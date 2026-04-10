package co.carrd.starkymods.interactions;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapThrowKickWindowCondition extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowKickWindowCondition> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowKickWindowCondition.class,
                            ShieldCapThrowKickWindowCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Allows ShieldCap throw kick only while the projectile-driven return reticle is actually visible.")
                    .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (context.getCommandBuffer() == null) {
            markFailed(context);
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            markFailed(context);
            return;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        UUID ownerUuid = uuidComponent == null ? null : uuidComponent.getUuid();
        if (ownerUuid == null || !ShieldCapThrowHomingService.isReturnKickWindowActive(ownerUuid, entityRef)) {
            markFailed(context);
        }
    }

    private static void markFailed(@Nonnull InteractionContext context) {
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
