package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class StarkyMjolnirDownstrikeBridge extends SimpleInstantInteraction {
    private static final String PRIMARY_ROOT_ID = "Root_Mjolnir_ConfigCheck_Downstrike_Charging";
    private static final String FALLBACK_ROOT_ID = "Root_Mjolnir_Control_Downstrike_OSC";

    @Nonnull
    public static final BuilderCodec<StarkyMjolnirDownstrikeBridge> CODEC =
            BuilderCodec
                    .builder(
                            StarkyMjolnirDownstrikeBridge.class,
                            StarkyMjolnirDownstrikeBridge::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Executes Mjolnir downstrike from ShieldCap when the player is airborne from a jump.")
                    .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (!isAirborneFromJump(context)) {
            markFailed(context);
            return;
        }

        RootInteraction fallbackRoot = RootInteraction.getAssetMap().getAsset(FALLBACK_ROOT_ID);
        if (fallbackRoot == null) {
            markFailed(context);
            return;
        }

        if (tryStartPrimaryDownstrike(context)) {
            return;
        }

        try {
            context.execute(fallbackRoot);
        } catch (Throwable ignored) {
            markFailed(context);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type,
                                    @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // Depends on server-side movement state and loaded cross-plugin assets.
    }

    private boolean isAirborneFromJump(@Nonnull InteractionContext context) {
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
        return movementStates != null
                && !movementStates.onGround
                && ShieldCapPrimaryJumpHitCooldown.isJumpMarked(context);
    }

    private boolean tryStartPrimaryDownstrike(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return false;
        }

        RootInteraction primaryRoot = RootInteraction.getAssetMap().getAsset(PRIMARY_ROOT_ID);
        if (primaryRoot == null) {
            return false;
        }

        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            playerRef = context.getOwningEntity();
        }
        if (playerRef == null || !playerRef.isValid()) {
            return false;
        }

        InteractionManager interactionManager = context.getInteractionManager();
        if (interactionManager == null) {
            interactionManager =
                    context.getCommandBuffer().getComponent(playerRef, InteractionModule.get().getInteractionManagerComponent());
        }
        if (interactionManager == null) {
            return false;
        }

        try {
            InteractionContext primaryContext =
                    InteractionContext.forInteraction(interactionManager, playerRef, InteractionType.Primary, context.getCommandBuffer());
            return interactionManager.tryStartChain(
                    playerRef,
                    context.getCommandBuffer(),
                    InteractionType.Primary,
                    primaryContext,
                    primaryRoot
            );
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void markFailed(@Nonnull InteractionContext context) {
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
