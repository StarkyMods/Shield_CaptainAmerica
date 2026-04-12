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

public final class ShieldCapFuriousOnslaughtTargetCooldown extends SimpleInstantInteraction {
    private static final long TARGET_COOLDOWN_MS = 300L;
    private static final Map<HitKey, Long> LAST_HIT_AT_MS = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<ShieldCapFuriousOnslaughtTargetCooldown> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapFuriousOnslaughtTargetCooldown.class,
                            ShieldCapFuriousOnslaughtTargetCooldown::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Prevents Furious Onslaught from affecting the same target more than once every 0.3 seconds.")
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
        UUID ownerUuid = resolveUuid(context.getOwningEntity(), context);
        UUID targetUuid = resolveUuid(context.getEntity(), context);
        if (ownerUuid == null || targetUuid == null) {
            return;
        }

        long now = System.currentTimeMillis();
        HitKey key = new HitKey(ownerUuid, targetUuid);
        Long lastHitAt = LAST_HIT_AT_MS.get(key);
        if (lastHitAt != null && (now - lastHitAt) < TARGET_COOLDOWN_MS) {
            markFailed(context);
            return;
        }

        LAST_HIT_AT_MS.put(key, now);
    }

    private static UUID resolveUuid(Ref<EntityStore> ref, @Nonnull InteractionContext context) {
        if (ref == null || !ref.isValid() || context.getCommandBuffer() == null) {
            return null;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(ref, UUIDComponent.getComponentType());
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

    private record HitKey(UUID ownerUuid, UUID targetUuid) {
    }
}
