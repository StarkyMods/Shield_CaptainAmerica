package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class ShieldCapTemporaryFallProtectionClear extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapTemporaryFallProtectionClear> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapTemporaryFallProtectionClear.class,
                            ShieldCapTemporaryFallProtectionClear::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Schedules temporary Fallstar fall-damage protection to end shortly after landing.")
                    .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        UUID ownerUuid = resolveOwnerUuid(context);
        if (ownerUuid != null) {
            ShieldCapGuardFallDamageReductionSystem.scheduleTemporaryProtectionClear(ownerUuid, 100L);
        }
    }

    private UUID resolveOwnerUuid(@Nonnull InteractionContext context) {
        Ref<EntityStore> ref = context.getOwningEntity();
        if (ref == null || !ref.isValid()) {
            ref = context.getEntity();
        }
        if (ref == null || !ref.isValid() || ref.getStore() == null) {
            return null;
        }

        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        return playerRef != null ? playerRef.getUuid() : null;
    }
}
