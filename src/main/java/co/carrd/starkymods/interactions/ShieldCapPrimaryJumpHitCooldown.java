package co.carrd.starkymods.interactions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapPrimaryJumpHitCooldown extends SimpleInstantInteraction {
    private static final String DOUBLE_JUMP_ROOT_ID = "Root_ShieldCap_Primary_Double_Jump_Internal";
    private static final String FALLSTAR_ROOT_ID = "Root_ShieldCap_Primary_Fallstar_Hit_Internal";
    private static final String FALLBACK_ROOT_ID = "Root_ShieldCap_Primary_Chain_Internal";
    private static final long COOLDOWN_MS = 1000L;
    private static final Map<UUID, Long> LAST_TRIGGERED_AT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> JUMP_MARKED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DOUBLE_JUMP_ATTEMPTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> FALLSTAR_ARMED = new ConcurrentHashMap<>();

    @Nonnull
    public static final BuilderCodec<ShieldCapPrimaryJumpHitCooldown> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapPrimaryJumpHitCooldown.class,
                            ShieldCapPrimaryJumpHitCooldown::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Applies a reliable cooldown to ShieldCap double jump before falling back to charging.")
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
        boolean airborne = isAirborne(context);
        boolean jumpMarked = ownerUuid != null && JUMP_MARKED.containsKey(ownerUuid);
        if (ownerUuid != null && airborne && !jumpMarked && isFreshJumpState(context)) {
            JUMP_MARKED.put(ownerUuid, Boolean.TRUE);
            jumpMarked = true;
        }

        if (ownerUuid == null || !airborne || !jumpMarked) {
            executeRoot(context, FALLBACK_ROOT_ID);
            return;
        }

        if (FALLSTAR_ARMED.remove(ownerUuid) != null) {
            executeRoot(context, FALLSTAR_ROOT_ID);
            return;
        }

        if (DOUBLE_JUMP_ATTEMPTED.containsKey(ownerUuid)) {
            executeRoot(context, FALLBACK_ROOT_ID);
            return;
        }

        boolean executedDoubleJump = executeWithCooldown(
                context,
                DOUBLE_JUMP_ROOT_ID,
                FALLBACK_ROOT_ID,
                COOLDOWN_MS,
                LAST_TRIGGERED_AT_MS
        );
        DOUBLE_JUMP_ATTEMPTED.put(ownerUuid, Boolean.TRUE);
        if (executedDoubleJump) {
            FALLSTAR_ARMED.put(ownerUuid, Boolean.TRUE);
        }
    }

    static boolean executeWithCooldown(@Nonnull InteractionContext context,
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
                return false;
            }
            lastTriggeredAtMs.put(ownerUuid, now);
        }

        executeRoot(context, actionRootId);
        return true;
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

    private static boolean isAirborne(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return false;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return false;
        }

        MovementStatesComponent movementComponent =
                context.getCommandBuffer().getComponent(entityRef, EntityModule.get().getMovementStatesComponentType());
        MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();
        return movementStates != null && !movementStates.onGround;
    }

    private static boolean isFreshJumpState(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return false;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return false;
        }

        MovementStatesComponent movementComponent =
                context.getCommandBuffer().getComponent(entityRef, EntityModule.get().getMovementStatesComponentType());
        MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();
        Velocity velocity = context.getCommandBuffer().getComponent(entityRef, EntityModule.get().getVelocityComponentType());
        double verticalVelocity = velocity != null && velocity.getVelocity() != null ? velocity.getVelocity().y : 0.0d;

        return movementStates != null
                && !movementStates.onGround
                && (movementStates.jumping || verticalVelocity > 0.1d);
    }

    static void executeRoot(@Nonnull InteractionContext context, @Nonnull String rootId) {
        ShieldCapRootChainExecutor.tryExecuteRoot(context, rootId);
    }

    public static void tickStore(Store<EntityStore> store) {
        if (store == null) {
            return;
        }

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                UUIDComponent uuidComponent = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
                UUID playerUuid = uuidComponent == null ? null : uuidComponent.getUuid();
                if (playerUuid == null) {
                    continue;
                }

                MovementStatesComponent movementComponent =
                        commandBuffer.getComponent(playerRef, EntityModule.get().getMovementStatesComponentType());
                MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();
                Velocity velocity = commandBuffer.getComponent(playerRef, EntityModule.get().getVelocityComponentType());
                double verticalVelocity = velocity != null && velocity.getVelocity() != null ? velocity.getVelocity().y : 0.0d;

                if (movementStates != null && (movementStates.jumping || (!movementStates.onGround && verticalVelocity > 0.1d))) {
                    JUMP_MARKED.put(playerUuid, Boolean.TRUE);
                }
                if (movementStates == null || movementStates.onGround) {
                    JUMP_MARKED.remove(playerUuid);
                    DOUBLE_JUMP_ATTEMPTED.remove(playerUuid);
                    FALLSTAR_ARMED.remove(playerUuid);
                    LAST_TRIGGERED_AT_MS.remove(playerUuid);
                }
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }
}
