package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapAirborneCondition extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapAirborneCondition> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapAirborneCondition.class,
                            ShieldCapAirborneCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Checks whether the player is airborne for ShieldCap primary jump routing.")
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
        if (!isAirborne(context)) {
            markFailed(context);
        }
    }

    private static boolean isAirborne(@Nonnull InteractionContext context) {
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

        MovementStatesComponent movementComponent =
                context.getCommandBuffer().getComponent(entityRef, EntityModule.get().getMovementStatesComponentType());
        MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();
        return movementStates != null && !movementStates.onGround;
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
