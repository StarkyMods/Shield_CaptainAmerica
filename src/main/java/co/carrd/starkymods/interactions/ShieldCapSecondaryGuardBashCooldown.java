package co.carrd.starkymods.interactions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapSecondaryGuardBashCooldown extends SimpleInstantInteraction {
    private static final long COOLDOWN_MS = 2000L;
    private static final Map<UUID, Long> LAST_TRIGGERED_AT_MS = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<ShieldCapSecondaryGuardBashCooldown> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapSecondaryGuardBashCooldown.class,
                            ShieldCapSecondaryGuardBashCooldown::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Applies a reliable cooldown gate for ShieldCap guard bash.")
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
        UUID ownerUuid = resolveEntityUuid(context);
        long now = System.currentTimeMillis();
        if (ownerUuid != null) {
            long lastTriggeredAt = LAST_TRIGGERED_AT_MS.getOrDefault(ownerUuid, 0L);
            if (now - lastTriggeredAt < COOLDOWN_MS) {
                markFailed(context);
                return;
            }
            LAST_TRIGGERED_AT_MS.put(ownerUuid, now);
        }
    }

    private static UUID resolveEntityUuid(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return null;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private static void markFailed(@Nonnull InteractionContext context) {
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
