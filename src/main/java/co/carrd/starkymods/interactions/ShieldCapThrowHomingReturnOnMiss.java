package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public class ShieldCapThrowHomingReturnOnMiss extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingReturnOnMiss> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingReturnOnMiss.class,
                            ShieldCapThrowHomingReturnOnMiss::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Returns the shield projectile to the owner when no next target exists.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        ShieldCapThrowHomingService.handleProjectileMiss(context);
    }
}
