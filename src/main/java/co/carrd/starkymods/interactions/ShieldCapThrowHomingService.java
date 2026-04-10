package co.carrd.starkymods.interactions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import co.carrd.starkymods.visuals.ShieldCapReturnReticleInjector;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapThrowHomingService {
    private static final boolean DEBUG = false;
    private static final String LOG_PREFIX = "[ShieldCapHomingDebug] ";

    private static final long HOMING_WINDOW_MS = 8000L;
    private static final long WORLD_TICK_INTERVAL_MS = 50L;
    private static final long PENDING_REF_GRACE_MS = 1000L;
    private static final long OWNER_RETURN_REMOVE_DELAY_MS = 0L;
    private static final long FLIGHT_RETURN_TIMEOUT_MS = 1400L;
    private static final long WALL_BOUNCE_DEBOUNCE_MS = 150L;
    private static final long THROW_KICK_RECENT_WINDOW_MS = 2000L;
    private static final long THROW_KICK_OWNER_CONTACT_GRACE_MS = 300L;
    private static final long THROW_KICK_TRACK_WINDOW_MS = 600000L;

    private static final int MAX_CHAIN_HITS = 5;
    private static final int MAX_WALL_BOUNCES = 2;

    private static final double TARGET_FORWARD_RANGE = 12.0;
    private static final double TARGET_LATERAL_RADIUS = 12.0;
    private static final double TARGET_HEIGHT = 15.0;
    private static final double FIRST_HOMING_FORWARD_RANGE = 1.8;
    private static final double FIRST_HOMING_VERTICAL_FORWARD_RANGE = TARGET_FORWARD_RANGE;
    private static final double FIRST_HOMING_LATERAL_RADIUS = 1.8;
    private static final double FIRST_HOMING_HEIGHT = 1.8;
    private static final double FIRST_HOMING_RANGE_STEP_DISTANCE = 10.0;
    private static final double FIRST_HOMING_RANGE_STEP_BONUS = 3.0;
    private static final double FIRST_HOMING_MAX_RANGE = 14.0;
    private static final double TARGET_AIM_HEIGHT_OFFSET = 1.2;
    private static final double HOMING_STRENGTH = 0.95;
    private static final double FIRST_WALL_BOUNCE_SPEED_MULTIPLIER = 0.75;
    private static final double THROW_KICK_BLOCK_BOUNCE_SPEED_MULTIPLIER = 0.82;
    private static final double MIN_PROJECTILE_SPEED = 16.0;
    private static final double MIN_TRACKED_SPEED = 0.5;
    private static final double STOP_STEER_DISTANCE = 0.1;
    private static final double IMPACT_UNSTICK_DISTANCE = 0.35;
    private static final double OWNER_RETURN_CATCH_DISTANCE = 1.8;
    private static final double OWNER_RETURN_SLOW_RADIUS = 12.0;
    private static final double OWNER_RETURN_CLOSE_SPEED_MULTIPLIER = 0.75;
    private static final double OWNER_RETURN_CLOSE_MIN_STEP = 0.12;
    private static final double RETURN_KICK_MIN_DISTANCE = 3.0;
    private static final double RETURN_KICK_MAX_DISTANCE = 7.0;
    private static final double THROW_KICK_VERTICAL_SURFACE_THRESHOLD = 0.6;
    private static final long RETURN_WINDOW_RETICLE_PULSE_INTERVAL_MS = 90L;
    private static final String THROW_KICK_ROOT_ID = "Root_ShieldCap_Throw_Kick";
    private static final String THROW_KICK_PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig_Throw_Kick";

    private static final Map<UUID, OwnerHomingState> ACTIVE_OWNER_STATES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_WORLD_TICK_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_RETURN_KICK_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECENT_THROW_KICK_USAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_THROW_KICK_RELAUNCHES = new ConcurrentHashMap<>();
    private static final Set<UUID> KNOWN_THROW_KICK_PROJECTILE_UUIDS = ConcurrentHashMap.newKeySet();

    private ShieldCapThrowHomingService() {
    }

    public static void enableFor(UUID ownerUuid) {
        enableFor(ownerUuid, null);
    }

    public static void enableFor(UUID ownerUuid, Ref<EntityStore> ownerRef) {
        if (ownerUuid == null) {
            return;
        }

        long now = System.currentTimeMillis();
        OwnerHomingState state = ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        Store<EntityStore> ownerStore = getValidStore(ownerRef);
        if (ownerStore != null && state.armedStore != null && state.armedStore != ownerStore) {
            state.trackedProjectiles.clear();
        }
        state.ownerRef = ownerRef;
        state.armedStore = ownerStore;
        state.enabledAtMs = now;
        state.expiresAtMs = now + HOMING_WINDOW_MS;
        state.trackedProjectiles.clear();
    }

    public static void markProjectile(UUID ownerUuid,
                                      Ref<EntityStore> ownerRef,
                                      Ref<EntityStore> projectileRef) {
        markProjectile(ownerUuid, ownerRef, projectileRef, false);
    }

    public static void markProjectile(UUID ownerUuid,
                                      Ref<EntityStore> ownerRef,
                                      Ref<EntityStore> projectileRef,
                                      boolean throwKickMode) {
        if (ownerUuid == null || projectileRef == null) {
            debug("markProjectile skipped | ownerUuid=" + ownerUuid + " | projectileRef=" + projectileRef);
            return;
        }
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(projectileRef)) {
            debug("markProjectile skipped ownerRef==projectileRef | ownerUuid=" + ownerUuid
                    + " | ownerRef=" + ownerRef);
            return;
        }
        if (!projectileRef.isValid()) {
            debug("markProjectile skipped invalid ref | ownerUuid=" + ownerUuid + " | projectileRef=" + projectileRef);
            return;
        }

        long now = System.currentTimeMillis();
        OwnerHomingState state = ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        Store<EntityStore> ownerStore = getValidStore(ownerRef);
        Store<EntityStore> projectileStore = getValidStore(projectileRef);
        Store<EntityStore> targetStore = projectileStore != null ? projectileStore : ownerStore;
        if (targetStore != null && state.armedStore != null && state.armedStore != targetStore) {
            state.trackedProjectiles.clear();
            state.ownerRef = null;
        }
        state.armedStore = targetStore != null ? targetStore : state.armedStore;
        if (ownerStore != null && ownerStore == state.armedStore) {
            state.ownerRef = ownerRef;
        }

        state.enabledAtMs = Math.min(state.enabledAtMs == 0L ? now : state.enabledAtMs, now);
        state.expiresAtMs = Math.max(state.expiresAtMs, now + (throwKickMode ? THROW_KICK_TRACK_WINDOW_MS : HOMING_WINDOW_MS));
        state.trackedProjectiles.compute(projectileRef, (ignored, existing) -> {
            TrackedProjectileState trackedState = existing != null ? existing : new TrackedProjectileState(now);
            if (throwKickMode) {
                rememberThrowKickProjectile(projectileStore != null ? projectileStore : ownerStore, projectileRef);
                trackedState.markAsThrowKick(now);
            } else if (isKnownThrowKickProjectile(projectileStore != null ? projectileStore : ownerStore, projectileRef)) {
                trackedState.markAsThrowKick(now);
            }
            return trackedState;
        });
    }

    public static void handleProjectileHit(InteractionContext context) {
        handleImpact(context, true);
    }

    public static void handleThrowKickProjectileHit(InteractionContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return;
        }

        long now = System.currentTimeMillis();
        OwnerHomingState ownerState =
                ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        ownerState.armedStore = store;
        ownerState.expiresAtMs = Math.max(ownerState.expiresAtMs, now + THROW_KICK_TRACK_WINDOW_MS);

        Ref<EntityStore> ownerRef = resolveOwnerRef(
                store.getExternalData() == null ? null : store.getExternalData().getWorld(),
                store,
                ownerState
        );

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectileRef);
            scheduleTrackedProjectileRemoval(store, ownerState, projectileRef);
            return;
        }

        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        rememberThrowKickProjectile(store, projectileRef);
        trackedState.markAsThrowKick(now);
        trackedState.throwKickStuckInBlock = false;
        trackedState.recordActivity(now);

        passThroughProjectile(store, projectileRef);
    }

    public static void handleProjectileMiss(InteractionContext context) {
        handleImpact(context, false);
    }

    public static void handleThrowKickBlockImpact(InteractionContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        Velocity projectileVelocity =
                context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
        if (physicsProvider == null || projectileVelocity == null) {
            return;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return;
        }

        long now = System.currentTimeMillis();
        OwnerHomingState ownerState =
                ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        ownerState.armedStore = store;
        ownerState.expiresAtMs = Math.max(ownerState.expiresAtMs, now + THROW_KICK_TRACK_WINDOW_MS);
        ownerState.lastReturnReticlePulseAtMs = 0L;

        Ref<EntityStore> ownerRef = resolveOwnerRef(
                store.getExternalData() == null ? null : store.getExternalData().getWorld(),
                store,
                ownerState
        );

        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        rememberThrowKickProjectile(store, projectileRef);
        trackedState.markAsThrowKick(now);
        trackedState.recordThrowKickBlockImpact(now);

        if (shouldThrowKickBounceFromBlock(physicsProvider)) {
            trackedState.throwKickStuckInBlock = false;
            trackedState.wallBounceCount++;
            trackedState.lastWallBounceAtMs = now;
            bounceThrowKickFromBlock(store, projectileRef, projectileVelocity, physicsProvider);
            return;
        }

        trackedState.throwKickStuckInBlock = true;
        stickThrowKickToBlock(store, projectileRef, projectileVelocity, physicsProvider);
    }

    public static void handleProjectileBounce(InteractionContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return;
        }

        OwnerHomingState ownerState =
                ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        ownerState.armedStore = store;
        ownerState.expiresAtMs = System.currentTimeMillis() + HOMING_WINDOW_MS;

        long now = System.currentTimeMillis();
        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        if (isKnownThrowKickProjectile(store, projectileRef)) {
            trackedState.markAsThrowKick(now);
        }
        trackedState.recordActivity(now);

        if (now - trackedState.lastWallBounceAtMs < WALL_BOUNCE_DEBOUNCE_MS) {
            return;
        }

        trackedState.wallBounceCount++;
        trackedState.lastWallBounceAtMs = now;
        if (trackedState.wallBounceCount == 1) {
            applyWallBounceSpeedReduction(context.getCommandBuffer().getStore(), projectileRef, physicsProvider);
        }

        debug("native wall bounce | projectileRef=" + projectileRef
                + " | wallBounces=" + trackedState.wallBounceCount
                + " | returning=" + trackedState.isReturningToOwner());
    }

    private static void applyWallBounceSpeedReduction(Store<EntityStore> store,
                                                      Ref<EntityStore> projectileRef,
                                                      StandardPhysicsProvider physicsProvider) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        Velocity projectileVelocity =
                store.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
        if (projectileVelocity == null || projectileVelocity.getVelocity() == null) {
            return;
        }

        Vector3d velocity = projectileVelocity.getVelocity();
        if (!velocity.isFinite() || velocity.closeToZero(1e-6)) {
            return;
        }

        Vector3d reducedVelocity = velocity.clone().scale(FIRST_WALL_BOUNCE_SPEED_MULTIPLIER);
        projectileVelocity.set(reducedVelocity);

        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(reducedVelocity);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(reducedVelocity);
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(reducedVelocity);
        }
    }

    public static void tripSafetyFuse(UUID ownerUuid) {
        tripSafetyFuse(ownerUuid, "unspecified");
    }

    public static void tripSafetyFuse(UUID ownerUuid, String reason) {
        if (ownerUuid == null) {
            return;
        }

        OwnerHomingState removed = ACTIVE_OWNER_STATES.remove(ownerUuid);
        if (removed != null) {
            removed.trackedProjectiles.clear();
        }

        debug("safetyFuse tripped | ownerUuid=" + ownerUuid + " | reason=" + reason);
    }

    public static void forceReturnToOwner(UUID ownerUuid) {
        if (ownerUuid == null) {
            return;
        }

        OwnerHomingState state = ACTIVE_OWNER_STATES.get(ownerUuid);
        if (state == null) {
            return;
        }

        long now = System.currentTimeMillis();
        state.expiresAtMs = Math.max(state.expiresAtMs, now + HOMING_WINDOW_MS);
        Store<EntityStore> store = state.armedStore;
        Ref<EntityStore> ownerRef = null;
        if (store != null && store.getExternalData() != null && store.getExternalData().getWorld() != null) {
            ownerRef = resolveOwnerRef(store.getExternalData().getWorld(), store, state);
        }

        recoverOwnerProjectilesForReturn(store, state, ownerRef, now);

        for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
            Ref<EntityStore> projectileRef = entry.getKey();
            TrackedProjectileState trackedProjectileState = entry.getValue();
            if (trackedProjectileState == null) {
                continue;
            }
            trackedProjectileState.startReturn(
                    trackedProjectileState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
            if (trackedProjectileState.throwKickMode) {
                trackedProjectileState.disableAutoReturn = false;
                trackedProjectileState.disableTargetSearch = true;
                trackedProjectileState.allowOwnerCatchWithoutReturn = true;
                trackedProjectileState.ownerContactGraceUntilMs = now;
            }
            trackedProjectileState.recordActivity(now);

            if (trackedProjectileState.throwKickMode && store != null && ownerRef != null) {
                unstickProjectileForReturn(store, projectileRef, ownerRef);
            }
        }
    }

    public static boolean queueReturnKickIfEligible(PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid()) {
            return false;
        }

        UUID ownerUuid = playerRef.getUuid();
        Ref<EntityStore> ownerRef = playerRef.getReference();
        if (ownerUuid == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        if (!isReturnKickWindowActive(ownerUuid, ownerRef)) {
            return false;
        }

        PENDING_RETURN_KICK_REQUESTS.put(ownerUuid, System.currentTimeMillis());
        return true;
    }

    public static boolean hasPendingReturnKickRequest(UUID ownerUuid) {
        return ownerUuid != null && PENDING_RETURN_KICK_REQUESTS.containsKey(ownerUuid);
    }

    public static boolean shouldSuppressPrimaryWhileReturnKick(UUID ownerUuid,
                                                               Ref<EntityStore> ownerRef) {
        if (ownerUuid == null) {
            return false;
        }
        if (hasPendingReturnKickRequest(ownerUuid)) {
            return true;
        }
        return ownerRef != null && ownerRef.isValid() && isReturnKickEligible(ownerUuid, ownerRef);
    }

    public static void clearPendingReturnKickRequest(UUID ownerUuid) {
        if (ownerUuid != null) {
            PENDING_RETURN_KICK_REQUESTS.remove(ownerUuid);
        }
    }

    public static void markRecentThrowKick(UUID ownerUuid) {
        if (ownerUuid != null) {
            RECENT_THROW_KICK_USAGE.put(ownerUuid, System.currentTimeMillis());
        }
    }

    public static boolean isReturnKickEligible(UUID ownerUuid, Ref<EntityStore> ownerRef) {
        if (ownerUuid == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        Store<EntityStore> store = ownerRef.getStore();
        if (store == null) {
            return false;
        }

        OwnerHomingState state = ACTIVE_OWNER_STATES.get(ownerUuid);
        if (state == null) {
            return false;
        }
        if (state.armedStore != null && state.armedStore != store) {
            return false;
        }
        if (hasReturningThrowKickProjectile(store, state)) {
            return false;
        }

        return hasReturningProjectileInKickRange(store, state, ownerRef, System.currentTimeMillis());
    }

    public static boolean isReturnKickWindowActive(UUID ownerUuid, Ref<EntityStore> ownerRef) {
        if (!isReturnKickEligible(ownerUuid, ownerRef)) {
            return false;
        }

        OwnerHomingState state = ACTIVE_OWNER_STATES.get(ownerUuid);
        if (state == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long visibleDurationMs = ShieldCapReturnReticleInjector.getReturnWindowDurationMs();
        return now - state.lastReturnReticlePulseAtMs <= visibleDurationMs;
    }

    public static void tick(PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        Holder<EntityStore> holder = playerRef.getHolder();
        if (holder == null) {
            return;
        }

        Player player = holder.getComponent(Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }

        tickStore(player.getWorld().getEntityStore().getStore());
    }

    public static void tickStore(Store<EntityStore> store) {
        if (store == null || ACTIVE_OWNER_STATES.isEmpty()) {
            return;
        }

        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }

        World world = entityStore.getWorld();
        long now = System.currentTimeMillis();
        String worldName = world.getName();
        long lastTick = LAST_WORLD_TICK_MS.getOrDefault(worldName, 0L);
        if (now - lastTick < WORLD_TICK_INTERVAL_MS) {
            return;
        }
        LAST_WORLD_TICK_MS.put(worldName, now);

        tickWorld(world, store, now);
        processPendingReturnKickRequests(store);
        processPendingThrowKickRelaunches(store);
    }

    public static void processPendingReturnKickRequests(Store<EntityStore> store) {
        if (store == null || PENDING_RETURN_KICK_REQUESTS.isEmpty() || ACTIVE_OWNER_STATES.isEmpty()) {
            return;
        }

        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(THROW_KICK_ROOT_ID);
        if (rootInteraction == null) {
            return;
        }

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerRefEntity = chunk.getReferenceTo(index);
                if (playerRefEntity == null || !playerRefEntity.isValid()) {
                    continue;
                }

                PlayerRef playerRef = commandBuffer.getComponent(playerRefEntity, PlayerRef.getComponentType());
                if (playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null) {
                    continue;
                }

                UUID ownerUuid = playerRef.getUuid();
                if (!PENDING_RETURN_KICK_REQUESTS.containsKey(ownerUuid)) {
                    continue;
                }

                try {
                    if (!isReturnKickEligible(ownerUuid, playerRefEntity)) {
                        continue;
                    }

                    InteractionManager interactionManager =
                            commandBuffer.getComponent(playerRefEntity, InteractionModule.get().getInteractionManagerComponent());
                    if (interactionManager == null) {
                        continue;
                    }

                    InteractionContext context =
                            InteractionContext.forInteraction(interactionManager, playerRefEntity, InteractionType.Primary, commandBuffer);
                    interactionManager.tryStartChain(playerRefEntity, commandBuffer, InteractionType.Primary, context, rootInteraction);
                } finally {
                    clearPendingReturnKickRequest(ownerUuid);
                }
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }

    public static void processPendingThrowKickRelaunches(Store<EntityStore> store) {
        if (store == null || PENDING_THROW_KICK_RELAUNCHES.isEmpty()) {
            return;
        }

        ProjectileConfig projectileConfig = ProjectileConfig.getAssetMap().getAsset(THROW_KICK_PROJECTILE_CONFIG_ID);
        if (projectileConfig == null) {
            return;
        }

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    continue;
                }

                PlayerRef playerRef = commandBuffer.getComponent(playerEntityRef, PlayerRef.getComponentType());
                if (playerRef == null || !playerRef.isValid() || playerRef.getUuid() == null) {
                    continue;
                }

                UUID ownerUuid = playerRef.getUuid();
                if (!PENDING_THROW_KICK_RELAUNCHES.containsKey(ownerUuid)) {
                    continue;
                }

                try {
                    com.hypixel.hytale.math.vector.Transform look = com.hypixel.hytale.server.core.util.TargetUtil.getLook(playerEntityRef, commandBuffer);
                    if (look == null || look.getPosition() == null || look.getDirection() == null) {
                        continue;
                    }

                    Ref<EntityStore> projectileRef = ProjectileModule.get().spawnProjectile(
                            null,
                            playerEntityRef,
                            commandBuffer,
                            projectileConfig,
                            look.getPosition(),
                            look.getDirection()
                    );
                    if (projectileRef == null || !projectileRef.isValid()) {
                        continue;
                    }

                    markProjectile(ownerUuid, playerEntityRef, projectileRef, true);
                } finally {
                    PENDING_THROW_KICK_RELAUNCHES.remove(ownerUuid);
                }
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }

    private static void handleImpact(InteractionContext context, boolean countHit) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return;
        }

        OwnerHomingState ownerState =
                ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        ownerState.armedStore = store;
        ownerState.expiresAtMs = System.currentTimeMillis() + HOMING_WINDOW_MS;

        Ref<EntityStore> ownerRef = resolveOwnerRef(
                store.getExternalData() == null ? null : store.getExternalData().getWorld(),
                store,
                ownerState
        );

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (countHit && ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectileRef);
            scheduleTrackedProjectileRemoval(store, ownerState, projectileRef);
            return;
        }

        long now = System.currentTimeMillis();
        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        trackedState.recordActivity(now);

        boolean shouldRebound = countHit;
        if (countHit) {
            registerHitTarget(context.getCommandBuffer().getStore(), trackedState, ownerRef, targetRef);
        } else {
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
            if (trackedState.throwKickMode && ownerRef != null && ownerRef.isValid()) {
                ownerState.lastReturnReticlePulseAtMs = 0L;
                unstickProjectileForReturn(store, projectileRef, ownerRef);
            }
        }

        if (trackedState.chainHitCount >= MAX_CHAIN_HITS) {
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
        }

        if (shouldRebound) {
            reboundProjectile(store, projectileRef, trackedState);
        }
    }

    private static void tickWorld(World world, Store<EntityStore> store, long nowMs) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                store.getResource(EntityModule.get().getEntitySpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return;
        }

        ACTIVE_OWNER_STATES.entrySet().removeIf(entry -> entry.getValue().expiresAtMs < nowMs);
        if (ACTIVE_OWNER_STATES.isEmpty()) {
            return;
        }

        for (OwnerHomingState state : ACTIVE_OWNER_STATES.values()) {
            if (state.armedStore != null && state.armedStore != store) {
                continue;
            }

            Ref<EntityStore> ownerRef = resolveOwnerRef(world, store, state);
            if (ownerRef == null || !ownerRef.isValid()) {
                scheduleAllTrackedProjectilesForRemoval(store, state);
                continue;
            }

            if (shouldTripSafetyFuseForOwner(store, state, ownerRef)) {
                state.trackedProjectiles.clear();
                state.expiresAtMs = nowMs - 1L;
                continue;
            }

            pulseReturnWindowReticleIfEligible(store, state, ownerRef, nowMs);

            List<ProjectileSnapshot> ownerProjectiles = collectTrackedProjectiles(state, store, ownerRef, nowMs);
            if (ownerProjectiles.isEmpty()) {
                continue;
            }

            TransformComponent ownerTransform =
                    store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
            Vector3d ownerAimPosition = ownerTransform == null ? null : getTargetAimPosition(ownerTransform);

            for (ProjectileSnapshot projectile : ownerProjectiles) {
                TrackedProjectileState trackedState = projectile.trackedState;
                if (trackedState == null) {
                    continue;
                }

                long autoReturnElapsedMs = trackedState.throwKickMode
                        ? nowMs - trackedState.lastThrowKickBlockImpactAtMs
                        : nowMs - trackedState.lastActivityAtMs;
                if (!trackedState.isReturningToOwner()
                        && !(trackedState.throwKickMode && trackedState.throwKickStuckInBlock)
                        && autoReturnElapsedMs >= FLIGHT_RETURN_TIMEOUT_MS) {
                    if (!trackedState.disableAutoReturn) {
                        trackedState.startReturn(
                                trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                                nowMs
                        );
                        if (trackedState.throwKickMode) {
                            state.lastReturnReticlePulseAtMs = 0L;
                            unstickProjectileForReturn(store, projectile.projectileRef, ownerRef);
                        }
                    }
                }

                if (trackedState.isReturningToOwner()) {
                    if (ownerAimPosition == null) {
                        scheduleTrackedProjectileRemoval(store, state, projectile.projectileRef);
                        continue;
                    }
                    double ownerDistance = projectile.transform.getPosition().distanceTo(ownerAimPosition);
                    if (ownerDistance <= OWNER_RETURN_CATCH_DISTANCE) {
                        if (queueThrowKickRelaunchIfEligible(state.ownerUuid, trackedState, nowMs)) {
                            scheduleTrackedProjectileRemoval(store, state, projectile.projectileRef);
                            continue;
                        }
                        ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectile.projectileRef);
                        scheduleTrackedProjectileRemoval(store, state, projectile.projectileRef);
                        continue;
                    }

                    steerProjectileToTarget(
                            projectile.projectileRef,
                            projectile.transform,
                            projectile.velocity,
                            projectile.physicsProvider,
                            ownerAimPosition,
                            ownerDistance < OWNER_RETURN_SLOW_RADIUS
                                    ? OWNER_RETURN_CLOSE_SPEED_MULTIPLIER
                                    : 1.0
                    );
                    phaseProjectileTowardTarget(
                            projectile.transform,
                            projectile.velocity,
                            projectile.physicsProvider,
                            ownerAimPosition,
                            ownerDistance < OWNER_RETURN_SLOW_RADIUS
                                    ? OWNER_RETURN_CLOSE_SPEED_MULTIPLIER
                                    : 1.0
                    );
                    continue;
                }

                if (ownerAimPosition != null
                        && canCatchOnOwnerContact(trackedState, nowMs)
                        && getOwnerCatchDistance(projectile.transform.getPosition(), ownerTransform, ownerAimPosition) <= OWNER_RETURN_CATCH_DISTANCE) {
                    ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectile.projectileRef);
                    scheduleTrackedProjectileRemoval(store, state, projectile.projectileRef);
                    continue;
                }

                if (trackedState.disableTargetSearch) {
                    continue;
                }

                TargetCandidate targetCandidate = findNearestTargetForProjectile(
                        store,
                        spatial,
                        projectile.projectileRef,
                        ownerRef,
                        projectile.transform.getPosition(),
                        projectile.velocity,
                        trackedState
                );
                if (targetCandidate == null) {
                    if (trackedState.chainHitCount > 0 || trackedState.wallBounceCount >= MAX_WALL_BOUNCES) {
                        trackedState.startReturn(ReturnMode.NORMAL, nowMs);
                        if (ownerAimPosition != null) {
                            steerProjectileToTarget(
                                    projectile.projectileRef,
                                    projectile.transform,
                                    projectile.velocity,
                                    projectile.physicsProvider,
                                    ownerAimPosition,
                                    1.0
                            );
                        } else {
                            scheduleTrackedProjectileRemoval(store, state, projectile.projectileRef);
                        }
                    }
                    continue;
                }

                TransformComponent targetTransform =
                        store.getComponent(targetCandidate.targetRef, EntityModule.get().getTransformComponentType());
                if (targetTransform == null || targetTransform.getPosition() == null) {
                    continue;
                }

                trackedState.recordActivity(nowMs);

                steerProjectileToTarget(
                        projectile.projectileRef,
                        projectile.transform,
                        projectile.velocity,
                        projectile.physicsProvider,
                        getTargetAimPosition(targetTransform),
                        1.0
                );
            }
        }
    }

    private static void pulseReturnWindowReticleIfEligible(Store<EntityStore> store,
                                                           OwnerHomingState state,
                                                           Ref<EntityStore> ownerRef,
                                                           long nowMs) {
        if (store == null || state == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        if (!hasReturningProjectileInKickRange(store, state, ownerRef, nowMs)) {
            return;
        }

        if (nowMs - state.lastReturnReticlePulseAtMs < RETURN_WINDOW_RETICLE_PULSE_INTERVAL_MS) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ownerRef, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        ShieldCapReturnReticleInjector.sendReturnWindowReticle(playerRef);
        state.lastReturnReticlePulseAtMs = nowMs;
    }

    private static boolean hasReturningProjectileInKickRange(Store<EntityStore> store,
                                                             OwnerHomingState state,
                                                             Ref<EntityStore> ownerRef,
                                                             long nowMs) {
        if (store == null || state == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        TransformComponent ownerTransform =
                store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        Vector3d ownerAimPosition = ownerTransform == null ? null : getTargetAimPosition(ownerTransform);
        if (ownerAimPosition == null) {
            return false;
        }

        for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
            Ref<EntityStore> projectileRef = entry.getKey();
            TrackedProjectileState trackedState = entry.getValue();
            if (projectileRef == null || trackedState == null || !trackedState.isReturningNormal() || isKnownThrowKickProjectile(store, projectileRef)) {
                continue;
            }
            if (projectileRef.getStore() != store || !projectileRef.isValid()) {
                continue;
            }

            TransformComponent projectileTransform =
                    store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
            if (projectileTransform == null || projectileTransform.getPosition() == null) {
                continue;
            }

            double distance = projectileTransform.getPosition().distanceTo(ownerAimPosition);
            if (Double.isFinite(distance)
                    && distance >= RETURN_KICK_MIN_DISTANCE
                    && distance <= RETURN_KICK_MAX_DISTANCE) {
                trackedState.recordActivity(nowMs);
                return true;
            }
        }

        return false;
    }

    private static boolean hasReturningThrowKickProjectile(Store<EntityStore> store,
                                                           OwnerHomingState state) {
        if (store == null || state == null) {
            return false;
        }

        for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
            Ref<EntityStore> projectileRef = entry.getKey();
            TrackedProjectileState trackedState = entry.getValue();
            if (projectileRef == null || trackedState == null) {
                continue;
            }
            if (!trackedState.throwKickMode || !trackedState.isReturningThrowKick()) {
                continue;
            }
            if (projectileRef.getStore() != store || !projectileRef.isValid()) {
                continue;
            }
            return true;
        }

        return false;
    }

    private static void registerHitTarget(Store<EntityStore> store,
                                          TrackedProjectileState trackedState,
                                          Ref<EntityStore> ownerRef,
                                          Ref<EntityStore> targetRef) {
        if (trackedState == null || targetRef == null || !targetRef.isValid()) {
            return;
        }
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    System.currentTimeMillis()
            );
            return;
        }
        if (isProjectileEntity(store, targetRef)) {
            return;
        }

        UUID targetUuid = getEntityUuid(store, targetRef);
        if (targetUuid != null && trackedState.hitTargetUuids.add(targetUuid)) {
            trackedState.chainHitCount++;
        }
    }

    private static void reboundProjectile(Store<EntityStore> store,
                                          Ref<EntityStore> projectileRef,
                                          TrackedProjectileState trackedState) {
        TransformComponent projectileTransform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        Velocity projectileVelocity =
                store.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
        StandardPhysicsProvider physicsProvider =
                store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null
                || projectileVelocity == null || physicsProvider == null) {
            return;
        }

        Vector3d currentVelocity = projectileVelocity.getVelocity();
        if (currentVelocity == null || !currentVelocity.isFinite()) {
            return;
        }

        double currentSpeed = currentVelocity.length();
        if (!Double.isFinite(currentSpeed) || currentSpeed < MIN_TRACKED_SPEED) {
            currentSpeed = MIN_PROJECTILE_SPEED;
        }

        Vector3d reversedVelocity = currentVelocity.clone();
        if (reversedVelocity.closeToZero(1e-6)) {
            reversedVelocity = new Vector3d(0.0, 0.0, -1.0);
        } else {
            reversedVelocity.normalize();
        }
        reversedVelocity.scale(-currentSpeed);

        projectileVelocity.set(reversedVelocity);
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(reversedVelocity);
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);

        Vector3d reversedDirection = reversedVelocity.clone();
        if (!reversedDirection.closeToZero(1e-6)) {
            reversedDirection.normalize();
            projectileTransform.setPosition(
                    projectileTransform.getPosition().clone().add(reversedDirection.scale(IMPACT_UNSTICK_DISTANCE))
            );
        }

        debug("impact rebound | projectileRef=" + projectileRef
                + " | chainHits=" + trackedState.chainHitCount
                + " | returning=" + trackedState.isReturningToOwner());
    }

    private static void passThroughProjectile(Store<EntityStore> store,
                                              Ref<EntityStore> projectileRef) {
        TransformComponent projectileTransform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        Velocity projectileVelocity =
                store.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
        StandardPhysicsProvider physicsProvider =
                store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null
                || projectileVelocity == null || physicsProvider == null) {
            return;
        }

        Vector3d currentVelocity = projectileVelocity.getVelocity();
        if (currentVelocity == null || !currentVelocity.isFinite()) {
            return;
        }

        Vector3d forward = currentVelocity.clone();
        if (forward.closeToZero(1e-6)) {
            return;
        }

        forward.normalize();
        projectileTransform.setPosition(
                projectileTransform.getPosition().clone().add(forward.scale(IMPACT_UNSTICK_DISTANCE))
        );
        projectileVelocity.set(currentVelocity);
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(currentVelocity);
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
    }

    private static boolean shouldThrowKickBounceFromBlock(StandardPhysicsProvider physicsProvider) {
        if (physicsProvider == null || physicsProvider.getContactNormal() == null) {
            return false;
        }

        Vector3d contactNormal = physicsProvider.getContactNormal();
        if (!contactNormal.isFinite() || contactNormal.closeToZero(1e-6)) {
            return false;
        }

        Vector3d normalizedNormal = contactNormal.clone().normalize();
        return Math.abs(normalizedNormal.y) >= THROW_KICK_VERTICAL_SURFACE_THRESHOLD;
    }

    private static void bounceThrowKickFromBlock(Store<EntityStore> store,
                                                 Ref<EntityStore> projectileRef,
                                                 Velocity projectileVelocity,
                                                 StandardPhysicsProvider physicsProvider) {
        if (store == null || projectileRef == null || !projectileRef.isValid()
                || projectileVelocity == null || physicsProvider == null) {
            return;
        }

        TransformComponent projectileTransform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null) {
            return;
        }

        Vector3d currentVelocity = projectileVelocity.getVelocity();
        Vector3d contactNormal = physicsProvider.getContactNormal();
        if (currentVelocity == null || !currentVelocity.isFinite()
                || contactNormal == null || !contactNormal.isFinite()
                || contactNormal.closeToZero(1e-6)) {
            return;
        }

        Vector3d normalizedNormal = contactNormal.clone().normalize();
        Vector3d reflectedVelocity = currentVelocity.clone()
                .subtract(normalizedNormal.clone().scale(2.0 * currentVelocity.dot(normalizedNormal)));
        if (!reflectedVelocity.isFinite() || reflectedVelocity.closeToZero(1e-6)) {
            reflectedVelocity = normalizedNormal.clone().scale(MIN_PROJECTILE_SPEED);
        } else {
            double reflectedSpeed = Math.max(MIN_PROJECTILE_SPEED, currentVelocity.length() * THROW_KICK_BLOCK_BOUNCE_SPEED_MULTIPLIER);
            reflectedVelocity.normalize().scale(reflectedSpeed);
        }

        Vector3d newPosition = projectileTransform.getPosition().clone().add(normalizedNormal.clone().scale(IMPACT_UNSTICK_DISTANCE));
        projectileTransform.setPosition(newPosition);
        projectileVelocity.set(reflectedVelocity);

        if (physicsProvider.getPosition() != null) {
            physicsProvider.getPosition().assign(newPosition);
        }
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(reflectedVelocity);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(reflectedVelocity.clone().normalize().scale(IMPACT_UNSTICK_DISTANCE));
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(reflectedVelocity.clone().normalize().scale(IMPACT_UNSTICK_DISTANCE));
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
    }

    private static void stickThrowKickToBlock(Store<EntityStore> store,
                                              Ref<EntityStore> projectileRef,
                                              Velocity projectileVelocity,
                                              StandardPhysicsProvider physicsProvider) {
        if (store == null || projectileRef == null || !projectileRef.isValid()
                || projectileVelocity == null || physicsProvider == null) {
            return;
        }

        TransformComponent projectileTransform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null) {
            return;
        }

        Vector3d contactNormal = physicsProvider.getContactNormal();
        Vector3d newPosition = projectileTransform.getPosition().clone();
        if (contactNormal != null && contactNormal.isFinite() && !contactNormal.closeToZero(1e-6)) {
            newPosition.add(contactNormal.clone().normalize().scale(IMPACT_UNSTICK_DISTANCE * 0.25));
            projectileTransform.setPosition(newPosition);
        }

        Vector3d zero = new Vector3d(0.0, 0.0, 0.0);
        projectileVelocity.set(zero);
        if (physicsProvider.getPosition() != null) {
            physicsProvider.getPosition().assign(newPosition);
        }
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(zero);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(zero);
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(zero);
        }
        try {
            physicsProvider.setState(Enum.valueOf(StandardPhysicsProvider.STATE.class, "INACTIVE"));
        } catch (IllegalArgumentException ignored) {
        }
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
    }

    private static void unstickProjectileForReturn(Store<EntityStore> store,
                                                   Ref<EntityStore> projectileRef,
                                                   Ref<EntityStore> ownerRef) {
        if (store == null || projectileRef == null || ownerRef == null
                || !projectileRef.isValid() || !ownerRef.isValid()) {
            return;
        }

        TransformComponent projectileTransform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        Velocity projectileVelocity =
                store.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
        StandardPhysicsProvider physicsProvider =
                store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        TransformComponent ownerTransform =
                store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        Vector3d ownerAimPosition = ownerTransform == null ? null : getTargetAimPosition(ownerTransform);
        if (projectileTransform == null || projectileTransform.getPosition() == null
                || projectileVelocity == null || physicsProvider == null || ownerAimPosition == null) {
            return;
        }

        Vector3d delta = ownerAimPosition.clone().subtract(projectileTransform.getPosition());
        if (!delta.isFinite() || delta.closeToZero(1e-6)) {
            return;
        }

        Vector3d launchDirection = delta.normalize();
        Vector3d launchVelocity = launchDirection.clone().scale(MIN_PROJECTILE_SPEED);
        projectileTransform.setPosition(projectileTransform.getPosition().clone().add(launchDirection.clone().scale(IMPACT_UNSTICK_DISTANCE)));
        projectileVelocity.set(launchVelocity);

        if (physicsProvider.getPosition() != null) {
            physicsProvider.getPosition().assign(projectileTransform.getPosition());
        }
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(launchVelocity);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(launchDirection.clone().scale(IMPACT_UNSTICK_DISTANCE));
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(launchDirection.clone().scale(IMPACT_UNSTICK_DISTANCE));
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
    }

    private static void recoverOwnerProjectilesForReturn(Store<EntityStore> store,
                                                         OwnerHomingState state,
                                                         Ref<EntityStore> ownerRef,
                                                         long now) {
        if (store == null || state == null) {
            return;
        }

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> projectileRef = chunk.getReferenceTo(index);
                if (projectileRef == null || !projectileRef.isValid()) {
                    continue;
                }
                if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(projectileRef)) {
                    continue;
                }
                if (commandBuffer.getComponent(projectileRef, Player.getComponentType()) != null) {
                    continue;
                }

                StandardPhysicsProvider physicsProvider =
                        commandBuffer.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
                if (physicsProvider == null) {
                    continue;
                }

                UUID creatorUuid = physicsProvider.getCreatorUuid();
                if (creatorUuid == null || !creatorUuid.equals(state.ownerUuid)) {
                    continue;
                }

                TrackedProjectileState trackedState =
                        state.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
                if (isKnownThrowKickProjectile(store, projectileRef)) {
                    trackedState.markAsThrowKick(now);
                }
                trackedState.startReturn(
                        trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                        now
                );
                trackedState.recordActivity(now);

                if (ownerRef != null && ownerRef.isValid()) {
                    unstickProjectileForReturn(store, projectileRef, ownerRef);
                }
            }
        };
        store.forEachChunk(ProjectileModule.get().getStandardPhysicsProviderComponentType(), chunkConsumer);
    }

    private static Ref<EntityStore> resolveProjectileRef(InteractionContext context) {
        Ref<EntityStore> entityRef = context.getEntity();
        if (isProjectileEntity(context.getCommandBuffer().getStore(), entityRef)) {
            return entityRef;
        }

        Ref<EntityStore> owningRef = context.getOwningEntity();
        if (isProjectileEntity(context.getCommandBuffer().getStore(), owningRef)) {
            return owningRef;
        }

        return null;
    }

    private static Ref<EntityStore> resolveOwnerRef(World world,
                                                    Store<EntityStore> store,
                                                    OwnerHomingState state) {
        if (state.ownerRef != null) {
            if (state.ownerRef.getStore() == store && state.ownerRef.isValid()) {
                state.armedStore = store;
                return state.ownerRef;
            }
            state.ownerRef = null;
        }

        if (world == null) {
            return null;
        }

        Ref<EntityStore> refFromUuid = world.getEntityRef(state.ownerUuid);
        if (refFromUuid != null && refFromUuid.isValid() && refFromUuid.getStore() == store) {
            state.ownerRef = refFromUuid;
            state.armedStore = store;
            return refFromUuid;
        }

        return null;
    }

    private static List<ProjectileSnapshot> collectTrackedProjectiles(OwnerHomingState state,
                                                                      Store<EntityStore> store,
                                                                      Ref<EntityStore> ownerRef,
                                                                      long nowMs) {
        List<ProjectileSnapshot> snapshots = new ArrayList<>();
        Iterator<Map.Entry<Ref<EntityStore>, TrackedProjectileState>> iterator = state.trackedProjectiles.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry = iterator.next();
            Ref<EntityStore> projectileRef = entry.getKey();
            TrackedProjectileState trackedState = entry.getValue();

            if (projectileRef == null || trackedState == null || projectileRef.getStore() != store) {
                iterator.remove();
                continue;
            }

            if (!projectileRef.isValid()) {
                long age = nowMs - trackedState.addedAtMs;
                if (age > PENDING_REF_GRACE_MS) {
                    iterator.remove();
                }
                continue;
            }

            if (projectileRef.equals(ownerRef)) {
                iterator.remove();
                continue;
            }

            TransformComponent projectileTransform =
                    store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
            Velocity projectileVelocity =
                    store.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
            StandardPhysicsProvider physicsProvider =
                    store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
            if (!isProjectileEntity(store, projectileRef) || physicsProvider == null) {
                iterator.remove();
                continue;
            }

            UUID creatorUuid = physicsProvider.getCreatorUuid();
            if (creatorUuid != null && !creatorUuid.equals(state.ownerUuid)) {
                iterator.remove();
                continue;
            }

            if (projectileTransform == null || projectileTransform.getPosition() == null || projectileVelocity == null) {
                iterator.remove();
                continue;
            }

            if (isKnownThrowKickProjectile(store, projectileRef) && !trackedState.throwKickMode) {
                trackedState.markAsThrowKick(nowMs);
            }

            Vector3d velocityVector = projectileVelocity.getVelocity();
            if (velocityVector == null || !velocityVector.isFinite()) {
                iterator.remove();
                continue;
            }
            if (velocityVector.length() < MIN_TRACKED_SPEED
                    && !trackedState.isReturningToOwner()
                    && !trackedState.persistWhenStationary) {
                iterator.remove();
                continue;
            }

            snapshots.add(new ProjectileSnapshot(projectileRef, projectileTransform, projectileVelocity, physicsProvider, trackedState));
        }

        return snapshots;
    }

    private static TargetCandidate findNearestTargetForProjectile(Store<EntityStore> store,
                                                                  SpatialResource<Ref<EntityStore>, EntityStore> spatial,
                                                                  Ref<EntityStore> projectileRef,
                                                                  Ref<EntityStore> ownerRef,
                                                                  Vector3d projectilePos,
                                                                  Velocity projectileVelocity,
                                                                  TrackedProjectileState trackedState) {
        if (projectilePos == null) {
            return null;
        }

        Vector3d forwardDir = getForwardDirection(projectileVelocity);
        if (forwardDir == null) {
            return null;
        }

        Ref<EntityStore> nearestTarget = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        List<Ref<EntityStore>> targetCandidates = new ArrayList<>();
        boolean firstSearchStillRestricted = trackedState != null
                && trackedState.chainHitCount <= 0
                && trackedState.wallBounceCount <= 0;
        boolean useNormalSearchVolume = !firstSearchStillRestricted
                || isVerticalTravel(projectileVelocity);
        double scaledFirstHomingRange = getScaledFirstHomingRange(store, ownerRef, projectilePos);
        double forwardRange = firstSearchStillRestricted
                ? (useNormalSearchVolume
                        ? Math.max(Math.min(TARGET_FORWARD_RANGE, FIRST_HOMING_MAX_RANGE), scaledFirstHomingRange)
                        : scaledFirstHomingRange)
                : TARGET_FORWARD_RANGE;
        double lateralRadius = firstSearchStillRestricted
                ? (useNormalSearchVolume
                        ? Math.max(Math.min(TARGET_LATERAL_RADIUS, FIRST_HOMING_MAX_RANGE), scaledFirstHomingRange)
                        : scaledFirstHomingRange)
                : TARGET_LATERAL_RADIUS;
        double targetHeight = firstSearchStillRestricted
                ? (useNormalSearchVolume
                        ? Math.max(Math.min(TARGET_HEIGHT, FIRST_HOMING_MAX_RANGE), scaledFirstHomingRange)
                        : scaledFirstHomingRange)
                : TARGET_HEIGHT;

        spatial.getSpatialStructure().collectCylinder(projectilePos, forwardRange, targetHeight, targetCandidates);

        for (Ref<EntityStore> targetRef : targetCandidates) {
            if (targetRef == null || targetRef.getStore() != store || !targetRef.isValid()
                    || targetRef.equals(projectileRef) || targetRef.equals(ownerRef)) {
                continue;
            }
            if (isTrackedProjectile(store, targetRef) || isProjectileEntity(store, targetRef)) {
                continue;
            }

            UUID targetUuid = getEntityUuid(store, targetRef);
            if (targetUuid != null && trackedState != null && trackedState.hitTargetUuids.contains(targetUuid)) {
                continue;
            }

            TransformComponent targetTransform =
                    store.getComponent(targetRef, EntityModule.get().getTransformComponentType());
            if (targetTransform == null || targetTransform.getPosition() == null) {
                continue;
            }

            Vector3d targetPos = getTargetAimPosition(targetTransform);
            double dy = Math.abs(targetPos.y - projectilePos.y);
            if (dy > targetHeight) {
                continue;
            }

            Vector3d delta = targetPos.clone().subtract(projectilePos);
            double distanceSq3d = delta.dot(delta);
            if (distanceSq3d < 1e-8) {
                continue;
            }

            double forwardDistance = delta.dot(forwardDir);
            if (forwardDistance <= 0.0 || forwardDistance > forwardRange) {
                continue;
            }

            double lateralDistanceSq = distanceSq3d - (forwardDistance * forwardDistance);
            if (lateralDistanceSq > lateralRadius * lateralRadius) {
                continue;
            }

            double distanceSq = projectilePos.distanceSquaredTo(targetPos);
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearestTarget = targetRef;
            }
        }

        return nearestTarget == null ? null : new TargetCandidate(nearestTarget);
    }

    private static Vector3d getForwardDirection(Velocity projectileVelocity) {
        if (projectileVelocity == null || projectileVelocity.getVelocity() == null) {
            return null;
        }

        Vector3d velocity = projectileVelocity.getVelocity();
        double speed = velocity.length();
        if (!Double.isFinite(speed) || speed < 1e-6) {
            return null;
        }

        return velocity.clone().normalize();
    }

    private static double getInitialForwardRange(Velocity projectileVelocity) {
        return isVerticalTravel(projectileVelocity)
                ? FIRST_HOMING_VERTICAL_FORWARD_RANGE
                : FIRST_HOMING_FORWARD_RANGE;
    }

    private static double getScaledFirstHomingRange(Store<EntityStore> store,
                                                    Ref<EntityStore> ownerRef,
                                                    Vector3d projectilePos) {
        if (store == null || ownerRef == null || !ownerRef.isValid() || projectilePos == null) {
            return FIRST_HOMING_FORWARD_RANGE;
        }

        TransformComponent ownerTransform =
                store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        if (ownerTransform == null || ownerTransform.getPosition() == null) {
            return FIRST_HOMING_FORWARD_RANGE;
        }

        double ownerDistance = projectilePos.distanceTo(ownerTransform.getPosition());
        if (!Double.isFinite(ownerDistance) || ownerDistance <= 0.0) {
            return FIRST_HOMING_FORWARD_RANGE;
        }

        int steps = (int) Math.floor(ownerDistance / FIRST_HOMING_RANGE_STEP_DISTANCE);
        double scaledRange = FIRST_HOMING_FORWARD_RANGE + (steps * FIRST_HOMING_RANGE_STEP_BONUS);
        return Math.min(scaledRange, FIRST_HOMING_MAX_RANGE);
    }

    private static boolean isVerticalTravel(Velocity projectileVelocity) {
        if (projectileVelocity == null || projectileVelocity.getVelocity() == null) {
            return false;
        }

        Vector3d velocity = projectileVelocity.getVelocity();
        if (!velocity.isFinite()) {
            return false;
        }

        double upward = velocity.y;
        if (upward <= 0.0d) {
            return false;
        }

        double horizontal = Math.sqrt((velocity.x * velocity.x) + (velocity.z * velocity.z));
        return upward >= (horizontal * Math.tan(Math.toRadians(22.0)));
    }

    private static Vector3d getTargetAimPosition(TransformComponent targetTransform) {
        Vector3d targetPos = targetTransform.getPosition();
        if (targetPos == null) {
            return null;
        }

        return targetPos.clone().add(0.0, TARGET_AIM_HEIGHT_OFFSET, 0.0);
    }

    private static boolean isTrackedProjectile(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (targetRef == null) {
            return false;
        }

        for (OwnerHomingState state : ACTIVE_OWNER_STATES.values()) {
            if (state.armedStore != null && state.armedStore != store) {
                continue;
            }
            for (Ref<EntityStore> trackedRef : state.trackedProjectiles.keySet()) {
                if (trackedRef == targetRef || (trackedRef != null && targetRef.equals(trackedRef))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isProjectileEntity(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }
        if (store.getComponent(ref, Player.getComponentType()) != null) {
            return false;
        }
        return store.getComponent(ref, ProjectileComponent.getComponentType()) != null
                || store.getComponent(ref, ProjectileModule.get().getProjectileComponentType()) != null
                || store.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType()) != null;
    }

    private static void steerProjectileToTarget(Ref<EntityStore> projectileRef,
                                                TransformComponent projectileTransform,
                                                Velocity projectileVelocity,
                                                StandardPhysicsProvider physicsProvider,
                                                Vector3d targetPosition,
                                                double speedMultiplier) {
        Vector3d projectilePos = projectileTransform.getPosition();
        if (projectilePos == null || targetPosition == null) {
            return;
        }

        double targetDistance = projectilePos.distanceTo(targetPosition);
        if (targetDistance <= STOP_STEER_DISTANCE) {
            return;
        }

        Vector3d currentVelocity = projectileVelocity.getVelocity();
        if (currentVelocity == null) {
            return;
        }

        double currentSpeed = currentVelocity.length();
        if (!Double.isFinite(currentSpeed) || currentSpeed < MIN_TRACKED_SPEED) {
            return;
        }

        double speed = Math.max(MIN_TRACKED_SPEED, Math.max(MIN_PROJECTILE_SPEED, currentSpeed) * speedMultiplier);
        Vector3d desiredDirection = targetPosition.clone().subtract(projectilePos);
        if (desiredDirection.closeToZero(1e-6)) {
            return;
        }

        desiredDirection.normalize().scale(speed);
        Vector3d blendedVelocity = currentVelocity.clone()
                .scale(1.0 - HOMING_STRENGTH)
                .add(desiredDirection.clone().scale(HOMING_STRENGTH));

        if (!blendedVelocity.isFinite()) {
            return;
        }

        projectileVelocity.set(blendedVelocity);
        if (physicsProvider != null && physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(blendedVelocity);
        }
        if (physicsProvider != null) {
            physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
            physicsProvider.setOnGround(false);
            physicsProvider.setSliding(false);
            physicsProvider.setBounced(false);
            physicsProvider.setMovedInsideSolid(false);
            physicsProvider.setVelocityExtremaCount(0);
        }
    }

    private static void phaseProjectileTowardTarget(TransformComponent projectileTransform,
                                                    Velocity projectileVelocity,
                                                    StandardPhysicsProvider physicsProvider,
                                                    Vector3d targetPosition,
                                                    double speedMultiplier) {
        Vector3d projectilePos = projectileTransform == null ? null : projectileTransform.getPosition();
        if (projectilePos == null || targetPosition == null) {
            return;
        }

        Vector3d delta = targetPosition.clone().subtract(projectilePos);
        double distance = delta.length();
        if (!Double.isFinite(distance) || distance <= STOP_STEER_DISTANCE) {
            return;
        }

        double currentSpeed = MIN_PROJECTILE_SPEED;
        if (projectileVelocity != null && projectileVelocity.getVelocity() != null) {
            double velocitySpeed = projectileVelocity.getVelocity().length();
            if (Double.isFinite(velocitySpeed) && velocitySpeed >= MIN_TRACKED_SPEED) {
                currentSpeed = Math.max(MIN_TRACKED_SPEED, velocitySpeed * speedMultiplier);
            }
        }

        double minStepDistance = speedMultiplier < 1.0 ? OWNER_RETURN_CLOSE_MIN_STEP : 0.35;
        double stepDistance = Math.min(distance, Math.max(minStepDistance, currentSpeed * (WORLD_TICK_INTERVAL_MS / 1000.0)));
        Vector3d step = delta.normalize().scale(stepDistance);
        Vector3d newPosition = projectilePos.clone().add(step);
        projectileTransform.setPosition(newPosition);

        double impliedSpeed = stepDistance / (WORLD_TICK_INTERVAL_MS / 1000.0);
        Vector3d stepVelocity = delta.normalize().scale(impliedSpeed);
        if (projectileVelocity != null) {
            projectileVelocity.set(stepVelocity);
        }

        if (physicsProvider != null) {
            if (physicsProvider.getPosition() != null) {
                physicsProvider.getPosition().assign(newPosition);
            }
            if (physicsProvider.getMovement() != null) {
                physicsProvider.getMovement().assign(step);
            }
            if (physicsProvider.getNextMovement() != null) {
                physicsProvider.getNextMovement().assign(step);
            }
            if (physicsProvider.getVelocity() != null) {
                physicsProvider.getVelocity().assign(stepVelocity);
            }
            physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
            physicsProvider.setOnGround(false);
            physicsProvider.setSliding(false);
            physicsProvider.setBounced(false);
            physicsProvider.setMovedInsideSolid(false);
            physicsProvider.setVelocityExtremaCount(0);
        }
    }

    private static boolean shouldTripSafetyFuseForOwner(Store<EntityStore> store,
                                                        OwnerHomingState state,
                                                        Ref<EntityStore> ownerRef) {
        if (ownerRef == null || ownerRef.getStore() != store || !ownerRef.isValid()) {
            return true;
        }
        if (state.trackedProjectiles.containsKey(ownerRef)) {
            return true;
        }
        if (store.getComponent(ownerRef, Player.getComponentType()) == null) {
            return true;
        }
        return isProjectileEntity(store, ownerRef);
    }

    private static boolean canCatchOnOwnerContact(TrackedProjectileState trackedState, long nowMs) {
        if (trackedState == null) {
            return false;
        }

        return nowMs >= trackedState.ownerContactGraceUntilMs
                && (trackedState.isReturningToOwner()
                || trackedState.chainHitCount > 0
                || trackedState.wallBounceCount > 0
                || trackedState.allowOwnerCatchWithoutReturn);
    }

    private static boolean queueThrowKickRelaunchIfEligible(UUID ownerUuid,
                                                            TrackedProjectileState trackedState,
                                                            long nowMs) {
        if (ownerUuid == null || trackedState == null || trackedState.throwKickMode) {
            return false;
        }

        Long lastKickAtMs = RECENT_THROW_KICK_USAGE.get(ownerUuid);
        if (lastKickAtMs == null) {
            return false;
        }
        if (nowMs - lastKickAtMs > THROW_KICK_RECENT_WINDOW_MS) {
            RECENT_THROW_KICK_USAGE.remove(ownerUuid, lastKickAtMs);
            return false;
        }

        RECENT_THROW_KICK_USAGE.remove(ownerUuid, lastKickAtMs);
        PENDING_THROW_KICK_RELAUNCHES.put(ownerUuid, nowMs);
        return true;
    }

    private static double getOwnerCatchDistance(Vector3d projectilePosition,
                                                TransformComponent ownerTransform,
                                                Vector3d ownerAimPosition) {
        if (projectilePosition == null) {
            return Double.MAX_VALUE;
        }

        double bestDistance = ownerAimPosition == null
                ? Double.MAX_VALUE
                : projectilePosition.distanceTo(ownerAimPosition);
        Vector3d ownerBasePosition = ownerTransform == null ? null : ownerTransform.getPosition();
        if (ownerBasePosition != null) {
            bestDistance = Math.min(bestDistance, projectilePosition.distanceTo(ownerBasePosition));
        }
        return bestDistance;
    }

    private static UUID getEntityUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private static void scheduleAllTrackedProjectilesForRemoval(Store<EntityStore> store, OwnerHomingState state) {
        for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
            scheduleTrackedProjectileRemoval(store, state, entry.getKey());
        }
    }

    private static void scheduleTrackedProjectileRemoval(Store<EntityStore> store,
                                                         OwnerHomingState state,
                                                         Ref<EntityStore> projectileRef) {
        if (store == null || state == null || projectileRef == null) {
            return;
        }

        state.trackedProjectiles.remove(projectileRef);
        forgetThrowKickProjectile(store, projectileRef);
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        ShieldCapSafeRemoveProjectile.scheduleSafeRemoval(store, projectileRef, projectileUuid, OWNER_RETURN_REMOVE_DELAY_MS);
    }

    private static void rememberThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_THROW_KICK_PROJECTILE_UUIDS.add(projectileUuid);
        }
    }

    private static void forgetThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
        }
    }

    private static boolean isKnownThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        return projectileUuid != null && KNOWN_THROW_KICK_PROJECTILE_UUIDS.contains(projectileUuid);
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println(LOG_PREFIX + message);
        }
    }

    private static Store<EntityStore> getValidStore(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref.getStore();
    }

    private static final class OwnerHomingState {
        private final UUID ownerUuid;
        private Ref<EntityStore> ownerRef;
        private Store<EntityStore> armedStore;
        private long enabledAtMs;
        private long expiresAtMs;
        private long lastReturnReticlePulseAtMs;
        private final Map<Ref<EntityStore>, TrackedProjectileState> trackedProjectiles = new ConcurrentHashMap<>();

        private OwnerHomingState(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }
    }

    private static final class TrackedProjectileState {
        private long addedAtMs;
        private long lastActivityAtMs;
        private int chainHitCount;
        private int wallBounceCount;
        private long lastWallBounceAtMs;
        private long lastThrowKickBlockImpactAtMs;
        private ReturnMode returnMode = ReturnMode.NONE;
        private boolean throwKickMode;
        private boolean throwKickStuckInBlock;
        private boolean disableAutoReturn;
        private boolean disableTargetSearch;
        private boolean allowOwnerCatchWithoutReturn;
        private boolean persistWhenStationary;
        private long ownerContactGraceUntilMs;
        private final Set<UUID> hitTargetUuids = ConcurrentHashMap.newKeySet();

        private TrackedProjectileState(long addedAtMs) {
            this.addedAtMs = addedAtMs;
            this.lastActivityAtMs = addedAtMs;
            this.lastThrowKickBlockImpactAtMs = addedAtMs;
        }

        private void recordActivity(long now) {
            this.lastActivityAtMs = now;
        }

        private void startReturn(ReturnMode returnMode, long now) {
            this.returnMode = returnMode;
            this.throwKickStuckInBlock = false;
            this.lastActivityAtMs = now;
        }

        private boolean isReturningToOwner() {
            return returnMode != ReturnMode.NONE;
        }

        private boolean isReturningNormal() {
            return returnMode == ReturnMode.NORMAL;
        }

        private boolean isReturningThrowKick() {
            return returnMode == ReturnMode.THROW_KICK;
        }

        private void markAsThrowKick(long now) {
            this.throwKickMode = true;
            this.returnMode = ReturnMode.NONE;
            this.disableAutoReturn = false;
            this.disableTargetSearch = true;
            this.allowOwnerCatchWithoutReturn = true;
            this.persistWhenStationary = true;
            this.lastThrowKickBlockImpactAtMs = Math.max(this.lastThrowKickBlockImpactAtMs, now);
            this.ownerContactGraceUntilMs = Math.max(this.ownerContactGraceUntilMs, now + THROW_KICK_OWNER_CONTACT_GRACE_MS);
        }

        private void recordThrowKickBlockImpact(long now) {
            this.lastThrowKickBlockImpactAtMs = now;
            this.lastActivityAtMs = now;
        }
    }

    private enum ReturnMode {
        NONE,
        NORMAL,
        THROW_KICK
    }

    private static final class TargetCandidate {
        private final Ref<EntityStore> targetRef;

        private TargetCandidate(Ref<EntityStore> targetRef) {
            this.targetRef = targetRef;
        }
    }

    private static final class ProjectileSnapshot {
        private final Ref<EntityStore> projectileRef;
        private final TransformComponent transform;
        private final Velocity velocity;
        private final StandardPhysicsProvider physicsProvider;
        private final TrackedProjectileState trackedState;

        private ProjectileSnapshot(Ref<EntityStore> projectileRef,
                                   TransformComponent transform,
                                   Velocity velocity,
                                   StandardPhysicsProvider physicsProvider,
                                   TrackedProjectileState trackedState) {
            this.projectileRef = projectileRef;
            this.transform = transform;
            this.velocity = velocity;
            this.physicsProvider = physicsProvider;
            this.trackedState = trackedState;
        }
    }
}



