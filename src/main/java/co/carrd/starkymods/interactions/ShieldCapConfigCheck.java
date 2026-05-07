package co.carrd.starkymods.interactions;

import co.carrd.starkymods.config.ShieldCapConfigManager;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

import javax.annotation.Nonnull;

public class ShieldCapConfigCheck extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapConfigCheck> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapConfigCheck.class,
                            ShieldCapConfigCheck::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Checks shieldcapconfig.json before allowing the configured Next interaction to run.")
                    .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (next == null || next.isBlank()) {
            markFailed(context);
            return;
        }

        if (!ShieldCapConfigManager.isConfigCheckedInteractionEnabled(next)) {
            markFailed(context);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type,
                                    @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // Server-only config gate.
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
