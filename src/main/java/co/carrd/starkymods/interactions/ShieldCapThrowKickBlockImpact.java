package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class ShieldCapThrowKickBlockImpact extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowKickBlockImpact> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowKickBlockImpact.class,
                            ShieldCapThrowKickBlockImpact::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Handles throw-kick projectile block impacts: lateral stick, vertical bounce, and timeout reset.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        ShieldCapThrowHomingService.handleThrowKickBlockImpact(context);
    }
}
