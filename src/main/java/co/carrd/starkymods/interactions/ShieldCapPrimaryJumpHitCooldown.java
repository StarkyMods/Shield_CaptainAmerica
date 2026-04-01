package co.carrd.starkymods.interactions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapPrimaryJumpHitCooldown extends SimpleInstantInteraction {
    private static final String ACTION_ROOT_ID = "Root_ShieldCap_Primary_Jump_Hit_Internal";
    private static final String FALLBACK_ROOT_ID = "Root_ShieldCap_Primary_Chain_Internal";
    private static final long COOLDOWN_MS = 1500L;
    private static final Map<UUID, Long> LAST_TRIGGERED_AT_MS = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<ShieldCapPrimaryJumpHitCooldown> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapPrimaryJumpHitCooldown.class,
                            ShieldCapPrimaryJumpHitCooldown::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Applies a reliable cooldown to ShieldCap jump hit before falling back to charging.")
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
        executeWithCooldown(context, ACTION_ROOT_ID, FALLBACK_ROOT_ID, COOLDOWN_MS, LAST_TRIGGERED_AT_MS);
    }

    static void executeWithCooldown(@Nonnull InteractionContext context,
                                    @Nonnull String actionRootId,
                                    @Nonnull String fallbackRootId,
                                    long cooldownMs,
                                    @Nonnull Map<UUID, Long> lastTriggeredAtMs) {
        UUID ownerUuid = resolveEntityUuid(context);
        long now = System.currentTimeMillis();
        if (ownerUuid != null) {
            long lastTriggeredAt = lastTriggeredAtMs.getOrDefault(ownerUuid, 0L);
            if (now - lastTriggeredAt < cooldownMs) {
                executeRoot(context, fallbackRootId);
                return;
            }
            lastTriggeredAtMs.put(ownerUuid, now);
        }

        executeRoot(context, actionRootId);
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

    static void executeRoot(@Nonnull InteractionContext context, @Nonnull String rootId) {
        ShieldCapRootChainExecutor.tryExecuteRoot(context, rootId);
    }
}
