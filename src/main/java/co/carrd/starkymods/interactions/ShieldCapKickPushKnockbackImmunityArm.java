package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class ShieldCapKickPushKnockbackImmunityArm extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapKickPushKnockbackImmunityArm> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapKickPushKnockbackImmunityArm.class,
                            ShieldCapKickPushKnockbackImmunityArm::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Arms temporary knockback immunity for the whole ShieldCap kick push chain.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (context.getCommandBuffer() == null) {
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        ShieldCapKickPushKnockbackImmunitySystem.arm(entityRef, context.getCommandBuffer());
    }
}
