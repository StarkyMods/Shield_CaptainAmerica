package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapPrimarySelector extends SimpleInstantInteraction {
    private static final String JUMP_ROOT_ID = "Root_ShieldCap_Primary_Jump_Hit_Gated";
    private static final String CROUCH_ROOT_ID = "Root_ShieldCap_Primary_Crouch_Chain_Gated";
    private static final String SPRINT_ROOT_ID = "Root_ShieldCap_Primary_Sprint_Hit_Gated";
    private static final String CHARGING_ROOT_ID = "Root_ShieldCap_Primary_Charging_Internal";

    @Nonnull
    public static final BuilderCodec<ShieldCapPrimarySelector> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapPrimarySelector.class,
                            ShieldCapPrimarySelector::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Routes ShieldCap primary to jump, crouch, sprint hit, or charging using Java movement checks.")
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
        String rootId = selectRoot(context);
        if (rootId == null || rootId.isBlank()) {
            return;
        }
        executeRoot(context, rootId);
    }

    private static String selectRoot(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return CHARGING_ROOT_ID;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return CHARGING_ROOT_ID;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null
                && ShieldCapThrowHomingService.shouldSuppressPrimaryWhileReturnKick(uuidComponent.getUuid(), entityRef)) {
            return null;
        }

        MovementStatesComponent movementComponent =
                context.getCommandBuffer().getComponent(entityRef, EntityModule.get().getMovementStatesComponentType());
        MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();

        if (movementStates != null && movementStates.jumping) {
            return JUMP_ROOT_ID;
        }
        if (movementStates != null && movementStates.crouching) {
            return CROUCH_ROOT_ID;
        }

        if (movementStates != null
                && !movementStates.jumping
                && movementStates.sprinting) {
            return SPRINT_ROOT_ID;
        }

        return CHARGING_ROOT_ID;
    }
    private static void executeRoot(@Nonnull InteractionContext context, @Nonnull String rootId) {
        ShieldCapRootChainExecutor.tryExecuteRoot(context, rootId);
    }
}
