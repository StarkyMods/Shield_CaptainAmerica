package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class ShieldCapThrowKickImpact extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowKickImpact> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowKickImpact.class,
                            ShieldCapThrowKickImpact::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Keeps the throw-kick projectile moving through enemies instead of rebounding or returning.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        ShieldCapThrowHomingService.handleThrowKickProjectileHit(context);
    }
}
