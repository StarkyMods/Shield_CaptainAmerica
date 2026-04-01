package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class ShieldCapGuardInactiveCondition extends SimpleInstantInteraction {
    private static final String GUARD_WIELD_INTERACTION_ID = "ShieldCap_Secondary_Guard_Wield";

    @Nonnull
    public static final BuilderCodec<ShieldCapGuardInactiveCondition> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapGuardInactiveCondition.class,
                            ShieldCapGuardInactiveCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Fails while ShieldCap guard wield is active so the normal primary does not leak through.")
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
        if (!isGuardInactive(context)) {
            markFailed(context);
        }
    }

    private static boolean isGuardInactive(@Nonnull InteractionContext context) {
        if (context.getInteractionManager() == null) {
            return true;
        }

        Boolean guardRunning = context.getInteractionManager().forEachInteraction(
                (chain, interaction, acc) -> {
                    if (acc) {
                        return true;
                    }
                    String id = interaction.getId();
                    return id != null && (GUARD_WIELD_INTERACTION_ID.equals(id) || id.contains(GUARD_WIELD_INTERACTION_ID));
                },
                Boolean.FALSE
        );

        return !Boolean.TRUE.equals(guardRunning);
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
