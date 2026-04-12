package co.carrd.starkymods.interactions;

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

public final class ShieldCapSignatureFuriousOnslaughtActiveCondition extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapSignatureFuriousOnslaughtActiveCondition> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapSignatureFuriousOnslaughtActiveCondition.class,
                            ShieldCapSignatureFuriousOnslaughtActiveCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Succeeds while Furious Onslaught is still active for the player.")
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
        if (!isActive(context)) {
            markFailed(context);
        }
    }

    private static boolean isActive(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return false;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return false;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        return uuidComponent != null
                && uuidComponent.getUuid() != null
                && ShieldCapSignatureFuriousOnslaughtService.isActive(uuidComponent.getUuid());
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
