package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public class ShieldCapThrowHomingBounce extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingBounce> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingBounce.class,
                            ShieldCapThrowHomingBounce::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Tracks native projectile wall bounces for the shield throw homing service.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        ShieldCapThrowHomingService.handleProjectileBounce(context);
    }
}
