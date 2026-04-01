package co.carrd.starkymods.interactions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class ShieldCapPrimarySprintHitCooldown extends SimpleInstantInteraction {
    private static final String ACTION_ROOT_ID = "Root_ShieldCap_Primary_Sprint_Hit_Internal";
    private static final String FALLBACK_ROOT_ID = "Root_ShieldCap_Primary_Chain_Internal";
    private static final long COOLDOWN_MS = 1800L;
    private static final Map<UUID, Long> LAST_TRIGGERED_AT_MS = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<ShieldCapPrimarySprintHitCooldown> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapPrimarySprintHitCooldown.class,
                            ShieldCapPrimarySprintHitCooldown::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Applies a reliable cooldown to ShieldCap sprint hit before falling back to charging.")
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
        ShieldCapPrimaryJumpHitCooldown.executeWithCooldown(
                context,
                ACTION_ROOT_ID,
                FALLBACK_ROOT_ID,
                COOLDOWN_MS,
                LAST_TRIGGERED_AT_MS
        );
    }
}
