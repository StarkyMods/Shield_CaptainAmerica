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

public final class ShieldCapTemporaryFallProtectionArm extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapTemporaryFallProtectionArm> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapTemporaryFallProtectionArm.class,
                            ShieldCapTemporaryFallProtectionArm::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Arms temporary fall-damage protection for Fallstar without requiring guard wield.")
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
            ShieldCapGuardFallDamageReductionSystem.armTemporaryProtection(ownerUuid);
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
