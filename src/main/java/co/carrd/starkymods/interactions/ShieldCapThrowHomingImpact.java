package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public class ShieldCapThrowHomingImpact extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingImpact> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingImpact.class,
                            ShieldCapThrowHomingImpact::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Handles chained bounce homing after a shield projectile impact.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        ShieldCapThrowHomingService.handleProjectileHit(context);
    }
}
