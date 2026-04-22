package co.carrd.starkymods.interactions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

public final class ShieldCapThrowHomingService {
    private static final boolean DEBUG = false;
    private static final String LOG_PREFIX = "[ShieldCapHomingDebug] ";
    private static final String MJOLNIR_ITEM_ID = "Weapon_Mjolnir_Starky";

    private static final long HOMING_WINDOW_MS = 8000L;
    private static final long WORLD_TICK_INTERVAL_MS = 50L;
    private static final long PENDING_REF_GRACE_MS = 1000L;
    private static final long OWNER_RETURN_REMOVE_DELAY_MS = 0L;
    private static final long FLIGHT_RETURN_TIMEOUT_MS = 1400L;
    private static final long WALL_BOUNCE_DEBOUNCE_MS = 150L;
    private static final long THROW_KICK_RECENT_WINDOW_MS = 2000L;
    private static final long THROW_KICK_OWNER_CONTACT_GRACE_MS = 300L;
    private static final long THROW_KICK_TRACK_WINDOW_MS = 600000L;
    private static final long RETURN_CALLING_CLEAR_RETRY_WINDOW_MS = 400L;
    private static final long RECENT_SHIELD_MARK_SYNC_GRACE_MS = 500L;
    private static final long LAST_KNOWN_SHIELD_SYNC_GRACE_MS = 8000L;
    private static final long OWNER_AUTO_RETURN_RETRY_INTERVAL_MS = 150L;
    private static final long RECENT_THROWN_HAND_GRACE_MS = 700L;

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
    private static final double THROW_KICK_SPAWN_RESOLVE_RANGE = 6.0;
    private static final double THROW_KICK_SPAWN_RESOLVE_HEIGHT = 8.0;
    private static final double THROW_KICK_SPAWN_MIN_SPEED = 8.0;
    private static final double NORMAL_BLOCK_BOUNCE_SPEED_MULTIPLIER = 0.95;
    private static final double PROJECTILE_CLASH_GROUND_FALL_SPEED = 14.0;
    private static final double PROJECTILE_CLASH_GROUND_DRIFT_MULTIPLIER = 0.45;
    private static final double TARGET_AIM_HEIGHT_OFFSET = 1.2;
    private static final double HOMING_STRENGTH = 0.95;
    private static final double FIRST_WALL_BOUNCE_SPEED_MULTIPLIER = 0.75;
    private static final double MIN_PROJECTILE_SPEED = 16.0;
    private static final double MIN_TRACKED_SPEED = 0.5;
    private static final double STOP_STEER_DISTANCE = 0.1;
    private static final double IMPACT_UNSTICK_DISTANCE = 0.35;
    private static final double PASS_THROUGH_UNSTICK_DISTANCE = 1.25;
    private static final double SAME_TARGET_POSITION_TOLERANCE = 0.9;
    private static final int TARGET_ROOT_RESOLVE_MAX_DEPTH = 8;
    private static final double OWNER_RETURN_CATCH_DISTANCE = 1.8;
    private static final double OWNER_RETURN_CALLING_TRIGGER_DISTANCE = 10.0;
    private static final double OWNER_RETURN_SLOW_RADIUS = 12.0;
    private static final double OWNER_RETURN_CLOSE_SPEED_MULTIPLIER = 0.75;
    private static final double OWNER_RETURN_CLOSE_MIN_STEP = 0.12;
    private static final double RETURN_KICK_MIN_DISTANCE = 6.0;
    private static final double RETURN_KICK_MAX_DISTANCE = 10.0;
    private static final double THROW_KICK_VERTICAL_SURFACE_THRESHOLD = 0.6;
    private static final long RETURN_WINDOW_RETICLE_PULSE_INTERVAL_MS = 45L;
    private static final long RETURN_CALLING_ANIMATION_DELAY_MS = 500L;
    private static final double PROJECTILE_CLASH_TOUCH_DISTANCE = 1.6;
    private static final String SHIELDCAP_PROJECTILE_ASSET_ID = "ShieldCap_Projectile";
    private static final String MJOLNIR_PROJECTILE_ASSET_ID = "Mjolnir";
    private static final String FOREIGN_PROJECTILE_ASSET_PREFIX = "Mjolnir";
    private static final String THROW_KICK_ROOT_ID = "Root_ShieldCap_Throw_Kick";
    private static final String THROW_KICK_PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig_Throw_Kick";
    private static final String RETURN_CALLING_ROOT_ID = "Root_ShieldCap_Return_Calling_Internal";
    private static final String RETURN_CALLING_CLEAR_ROOT_ID = "Root_ShieldCap_Return_Calling_Clear_Internal";
    private static final String NORMAL_IMPACT_SOUND_ID = "SFX_ShieldCap_Hit";
    private static final String THROW_KICK_IMPACT_SOUND_ID = "SFX_ShieldCap_SmallImpact";
    private static final String IMPACT_PARTICLE_ID = "Shield_Bash";
    private static final double IMPACT_PARTICLE_Y_OFFSET = 0.5;
    private static final float NORMAL_IMPACT_PARTICLE_SCALE = 1.0f;
    private static final float THROW_KICK_IMPACT_PARTICLE_SCALE = 1.4f;
    private static final float IMPACT_SOUND_VOLUME_MODIFIER = 1.0f;

    private static final Map<UUID, OwnerHomingState> ACTIVE_OWNER_STATES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_WORLD_TICK_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_RETURN_KICK_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_RETURN_CALLING_EFFECT_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_RETURN_CALLING_CLEAR_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECENT_THROW_KICK_USAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_THROW_KICK_RELAUNCHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_THROW_KICK_MARKS = new ConcurrentHashMap<>();
    private static final Set<UUID> KNOWN_SHIELDCAP_PROJECTILE_UUIDS = ConcurrentHashMap.newKeySet();
    private static final Set<Ref<EntityStore>> KNOWN_SHIELDCAP_PROJECTILE_REFS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> KNOWN_THROW_KICK_PROJECTILE_UUIDS = ConcurrentHashMap.newKeySet();
    private static final Set<Ref<EntityStore>> KNOWN_THROW_KICK_PROJECTILE_REFS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<UUID>> PROJECTILE_PHASE_HIT_TARGET_UUIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Ref<EntityStore>>> PROJECTILE_PHASE_HIT_TARGET_REFS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Vector3d>> PROJECTILE_PHASE_HIT_TARGET_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Set<UUID>> PROJECTILE_PHASE_HIT_TARGET_UUIDS_BY_REF = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Set<Ref<EntityStore>>> PROJECTILE_PHASE_HIT_TARGET_REFS_BY_REF = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, List<Vector3d>> PROJECTILE_PHASE_HIT_TARGET_POSITIONS_BY_REF = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> THROW_KICK_RETURN_DEADLINES = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> THROW_KICK_RETURN_DEADLINES_BY_REF = new ConcurrentHashMap<>();
    private static final Set<UUID> RETURNING_THROW_KICK_PROJECTILE_UUIDS = ConcurrentHashMap.newKeySet();
    private static final Set<Ref<EntityStore>> RETURNING_THROW_KICK_PROJECTILE_REFS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STUCK_THROW_KICK_PROJECTILE_UUIDS = ConcurrentHashMap.newKeySet();
    private static final Set<Ref<EntityStore>> STUCK_THROW_KICK_PROJECTILE_REFS = ConcurrentHashMap.newKeySet();
    private static volatile boolean MJOLNIR_CLASH_BRIDGE_LOOKUP_ATTEMPTED = false;
    private static volatile boolean MJOLNIR_CLASH_BRIDGE_AVAILABLE = true;
    private static Method mjolnirProjectileReturningForClashMethod;
    private static Method mjolnirForceProjectileClashImpactMethod;

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
        if (!throwKickMode) {
            state.lastShieldProjectileMarkedAtMs = now;
        }
        state.trackedProjectiles.compute(projectileRef, (ignored, existing) -> {
            TrackedProjectileState trackedState = existing != null ? existing : new TrackedProjectileState(now);
            rememberShieldCapProjectile(projectileStore != null ? projectileStore : ownerStore, projectileRef);
            if (!throwKickMode) {
                state.lastKnownShieldProjectileRef = projectileRef;
                ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                        resolveActiveOrRecentThrownHand(
                                projectileStore != null ? projectileStore : ownerStore,
                                state,
                                ownerRef,
                                now
                        );
                if (activeHand != ShieldCapThrownHandResolver.ActiveThrownHand.NONE) {
                    state.lastObservedThrownHand = activeHand;
                    state.lastObservedThrownHandAtMs = now;
                }
            }
            if (throwKickMode) {
                rememberThrowKickProjectile(projectileStore != null ? projectileStore : ownerStore, projectileRef);
                armThrowKickReturnTimeout(projectileStore != null ? projectileStore : ownerStore, projectileRef, now);
                trackedState.markAsThrowKick(now);
                debug("throwKick marked | owner=" + ownerUuid
                        + " | projectile=" + getEntityUuid(projectileStore != null ? projectileStore : ownerStore, projectileRef)
                        + " | deadline=" + getThrowKickReturnDeadline(projectileStore != null ? projectileStore : ownerStore, projectileRef, -1L));
            } else if (isKnownThrowKickProjectile(projectileStore != null ? projectileStore : ownerStore, projectileRef)) {
                trackedState.markAsThrowKick(now);
                debug("throwKick re-marked from known set | owner=" + ownerUuid
                        + " | projectile=" + getEntityUuid(projectileStore != null ? projectileStore : ownerStore, projectileRef)
                        + " | deadline=" + getThrowKickReturnDeadline(projectileStore != null ? projectileStore : ownerStore, projectileRef, -1L));
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

        Ref<EntityStore> targetRef = normalizeTargetRef(store, context.getTargetEntity());
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectileRef);
            scheduleTrackedProjectileRemoval(store, ownerState, projectileRef);
            return;
        }

        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        rememberThrowKickProjectile(store, projectileRef);
        armThrowKickReturnTimeout(store, projectileRef, now);
        trackedState.markAsThrowKick(now);
        if (trackedState.throwKickStuckInBlock || isThrowKickStuck(store, projectileRef)) {
            trackedState.throwKickStuckInBlock = true;
            debug("throwKick hit ignored while stuck | owner=" + ownerUuid
                    + " | projectile=" + getEntityUuid(store, projectileRef)
                    + " | target=" + getEntityUuid(store, targetRef));
            return;
        }
        trackedState.throwKickStuckInBlock = false;
        trackedState.recordActivity(now);
        playProjectileImpactFx(store, projectileRef, THROW_KICK_IMPACT_SOUND_ID, IMPACT_PARTICLE_ID, THROW_KICK_IMPACT_PARTICLE_SCALE);
        debug("throwKick enemy hit passthrough | owner=" + ownerUuid
                + " | projectile=" + getEntityUuid(store, projectileRef)
                + " | deadline=" + getThrowKickReturnDeadline(store, projectileRef, -1L));

        passThroughProjectile(store, projectileRef, PASS_THROUGH_UNSTICK_DISTANCE);
    }

    static boolean shouldApplyThrowKickDamageToTarget(InteractionContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return false;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        Ref<EntityStore> targetRef = normalizeTargetRef(store, context.getTargetEntity());
        if (store == null || projectileRef == null || !projectileRef.isValid()
                || targetRef == null || !targetRef.isValid()) {
            return false;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return false;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return false;
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
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            return false;
        }
        if (isProjectileEntity(store, targetRef)) {
            return false;
        }

        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        rememberThrowKickProjectile(store, projectileRef);
        trackedState.markAsThrowKick(now);
        if (trackedState.throwKickStuckInBlock || isThrowKickStuck(store, projectileRef)) {
            trackedState.throwKickStuckInBlock = true;
            return false;
        }

        UUID targetUuid = getEntityUuid(store, targetRef);
        if ((targetUuid != null && trackedState.hitTargetUuids.contains(targetUuid))
                || trackedState.hitTargetRefs.contains(targetRef)
                || hasProjectilePhaseHitTarget(store, projectileRef, targetRef, targetUuid)) {
            debug("throwKick repeat damage blocked | owner=" + ownerUuid
                    + " | projectile=" + getEntityUuid(store, projectileRef)
                    + " | target=" + targetUuid
                    + " | returning=" + trackedState.isReturningToOwner());
            return false;
        }

        registerHitTarget(store, projectileRef, trackedState, ownerRef, targetRef);
        return true;
    }

    static boolean shouldApplyThrownShieldDamageToTarget(InteractionContext context) {
        if (context == null || context.getCommandBuffer() == null) {
            return false;
        }

        Store<EntityStore> store = context.getCommandBuffer().getStore();
        Ref<EntityStore> projectileRef = resolveProjectileRef(context);
        Ref<EntityStore> targetRef = normalizeTargetRef(store, context.getTargetEntity());
        if (store == null || projectileRef == null || !projectileRef.isValid()
                || targetRef == null || !targetRef.isValid()) {
            return false;
        }

        StandardPhysicsProvider physicsProvider =
                context.getCommandBuffer().getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return false;
        }

        UUID ownerUuid = physicsProvider.getCreatorUuid();
        if (ownerUuid == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        OwnerHomingState ownerState =
                ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, ignored -> new OwnerHomingState(ownerUuid));
        ownerState.armedStore = store;
        ownerState.expiresAtMs = Math.max(ownerState.expiresAtMs, now + HOMING_WINDOW_MS);

        Ref<EntityStore> ownerRef = resolveOwnerRef(
                store.getExternalData() == null ? null : store.getExternalData().getWorld(),
                store,
                ownerState
        );
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            return false;
        }
        if (isProjectileEntity(store, targetRef)) {
            return false;
        }

        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        trackedState.recordActivity(now);

        UUID targetUuid = getEntityUuid(store, targetRef);
        if ((targetUuid != null && trackedState.hitTargetUuids.contains(targetUuid))
                || trackedState.hitTargetRefs.contains(targetRef)
                || hasProjectilePhaseHitTarget(store, projectileRef, targetRef, targetUuid)) {
            debug("throw repeat damage blocked | owner=" + ownerUuid
                    + " | projectile=" + getEntityUuid(store, projectileRef)
                    + " | target=" + targetUuid
                    + " | returning=" + trackedState.isReturningToOwner());
            return false;
        }

        registerHitTarget(store, projectileRef, trackedState, ownerRef, targetRef);
        return true;
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
        clearReturnKickWindow(ownerState);

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

        trackedState.throwKickStuckInBlock = true;
        trackedState.ownerContactGraceUntilMs = now;
        markThrowKickStuck(store, projectileRef);
        playThrowKickStickFx(store, projectileRef);
        debug("throwKick stuck in block | owner=" + ownerUuid
                + " | projectile=" + getEntityUuid(store, projectileRef));
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
            trackedState.recordThrowKickBlockImpact(now);

            if (!shouldThrowKickBounceFromBlock(physicsProvider)) {
                trackedState.throwKickStuckInBlock = true;
                trackedState.ownerContactGraceUntilMs = now;
                markThrowKickStuck(store, projectileRef);
                playThrowKickStickFx(store, projectileRef);
                debug("throwKick lateral bounce converted to stick | owner=" + ownerUuid
                        + " | projectile=" + getEntityUuid(store, projectileRef));
                Velocity projectileVelocity =
                        context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
                if (projectileVelocity != null) {
                    stickThrowKickToBlock(store, projectileRef, projectileVelocity, physicsProvider);
                }
                return;
            }

            clearThrowKickStuck(store, projectileRef);
            resetThrowKickReturnTimeout(store, projectileRef, now);
            trackedState.throwKickStuckInBlock = false;
        }
        trackedState.recordActivity(now);

        if (now - trackedState.lastWallBounceAtMs < WALL_BOUNCE_DEBOUNCE_MS) {
            return;
        }

        trackedState.wallBounceCount++;
        trackedState.lastWallBounceAtMs = now;
        if (trackedState.throwKickMode) {
            playProjectileImpactFx(store, projectileRef, THROW_KICK_IMPACT_SOUND_ID, IMPACT_PARTICLE_ID, THROW_KICK_IMPACT_PARTICLE_SCALE);
        } else {
            playProjectileImpactFx(store, projectileRef, NORMAL_IMPACT_SOUND_ID, IMPACT_PARTICLE_ID, NORMAL_IMPACT_PARTICLE_SCALE);
        }
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

    public static boolean forceReturnToOwner(UUID ownerUuid) {
        return forceReturnToOwner(ownerUuid, null);
    }

    public static boolean forceReturnToOwner(UUID ownerUuid, Ref<EntityStore> ownerRefHint) {
        if (ownerUuid == null) {
            return false;
        }

        OwnerHomingState state = ACTIVE_OWNER_STATES.computeIfAbsent(ownerUuid, OwnerHomingState::new);

        long now = System.currentTimeMillis();
        state.expiresAtMs = Math.max(state.expiresAtMs, now + HOMING_WINDOW_MS);
        Store<EntityStore> store = state.armedStore;
        Ref<EntityStore> ownerRef = ownerRefHint;
        if (ownerRef != null && ownerRef.isValid()) {
            state.ownerRef = ownerRef;
            if (store == null) {
                store = ownerRef.getStore();
                state.armedStore = store;
            }
        }
        if (store != null && store.getExternalData() != null && store.getExternalData().getWorld() != null) {
            Ref<EntityStore> resolvedOwnerRef = resolveOwnerRef(store.getExternalData().getWorld(), store, state);
            if (resolvedOwnerRef != null && resolvedOwnerRef.isValid()) {
                ownerRef = resolvedOwnerRef;
                state.ownerRef = resolvedOwnerRef;
            }
        }

        restoreLastKnownShieldProjectileRef(store, state, now);
        syncKnownShieldCapProjectiles(store, state, ownerRef, now);
        recoverOwnerProjectilesForReturn(store, state, ownerRef, now);
        debug("forceReturnToOwner begin | owner=" + ownerUuid
                + " | tracked=" + state.trackedProjectiles.size()
                + " | ownerRefValid=" + (ownerRef != null && ownerRef.isValid())
                + " | handState=" + describeHeldShieldState(store, ownerRef));

        boolean foundProjectile = false;

        for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
            Ref<EntityStore> projectileRef = entry.getKey();
            TrackedProjectileState trackedProjectileState = entry.getValue();
            if (trackedProjectileState == null) {
                continue;
            }
            foundProjectile = true;
            boolean needsUnstickForReturn =
                    trackedProjectileState.throwKickMode
                            || trackedProjectileState.fallingAfterProjectileClash
                            || trackedProjectileState.groundedAfterProjectileClash;
            trackedProjectileState.clearMjolnirProjectileClash();
            trackedProjectileState.clearProjectileClashGroundState();
            clearProjectilePhaseHitTargets(store, projectileRef);
            trackedProjectileState.startReturn(
                    trackedProjectileState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
            if (trackedProjectileState.throwKickMode) {
                markThrowKickReturning(store, projectileRef);
                clearThrowKickStuck(store, projectileRef);
                trackedProjectileState.disableAutoReturn = false;
                trackedProjectileState.disableTargetSearch = true;
                trackedProjectileState.allowOwnerCatchWithoutReturn = true;
                trackedProjectileState.ownerContactGraceUntilMs = now;
                debug("throwKick force return armed | owner=" + ownerUuid
                        + " | projectile=" + getEntityUuid(store, projectileRef));
            }
            trackedProjectileState.recordActivity(now);

            if (needsUnstickForReturn && store != null && ownerRef != null) {
                unstickProjectileForReturn(store, projectileRef, ownerRef);
            }
        }

        debug("forceReturnToOwner end | owner=" + ownerUuid
                + " | foundProjectile=" + foundProjectile
                + " | trackedAfter=" + state.trackedProjectiles.size());
        return foundProjectile;
    }

    public static Ref<EntityStore> findNearbyProjectileForMjolnirClash(Store<EntityStore> store,
                                                                       Ref<EntityStore> mjolnirProjectileRef,
                                                                       Vector3d searchOrigin,
                                                                       double radius,
                                                                       double height) {
        if (store == null || searchOrigin == null || !Double.isFinite(radius) || radius <= 0.0) {
            return null;
        }

        double maxDistanceSq = radius * radius;
        double maxHeight = Math.max(0.0, height);
        Ref<EntityStore> nearestProjectile = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (OwnerHomingState state : ACTIVE_OWNER_STATES.values()) {
            if (state == null || (state.armedStore != null && state.armedStore != store)) {
                continue;
            }

            for (Map.Entry<Ref<EntityStore>, TrackedProjectileState> entry : state.trackedProjectiles.entrySet()) {
                Ref<EntityStore> projectileRef = entry.getKey();
                TrackedProjectileState trackedState = entry.getValue();
                if (isMjolnirProjectileReturningForClash(store, mjolnirProjectileRef)) {
                    continue;
                }
                if (!isEligibleForMjolnirProjectileClash(store, projectileRef, trackedState, mjolnirProjectileRef)) {
                    continue;
                }

                Vector3d projectilePos = resolveProjectilePosition(store, projectileRef);
                if (projectilePos == null || Math.abs(projectilePos.y - searchOrigin.y) > maxHeight) {
                    continue;
                }

                double distanceSq = searchOrigin.distanceSquaredTo(projectilePos);
                if (!Double.isFinite(distanceSq) || distanceSq > maxDistanceSq || distanceSq >= nearestDistanceSq) {
                    continue;
                }

                nearestProjectile = projectileRef;
                nearestDistanceSq = distanceSq;
            }
        }

        return nearestProjectile;
    }

    public static boolean beginMjolnirProjectileClash(Store<EntityStore> store,
                                                      Ref<EntityStore> shieldProjectileRef,
                                                      Ref<EntityStore> mjolnirProjectileRef,
                                                      long nowMs) {
        TrackedProjectileHandle handle = findTrackedProjectileHandle(store, shieldProjectileRef);
        if (handle == null
                || !isEligibleForMjolnirProjectileClash(store, shieldProjectileRef, handle.trackedState, mjolnirProjectileRef)) {
            return false;
        }

        handle.trackedState.beginMjolnirProjectileClash(mjolnirProjectileRef);
        handle.trackedState.recordActivity(nowMs);
        clearReturnKickWindow(handle.ownerState);
        debug("mjolnir clash armed | owner=" + handle.ownerState.ownerUuid
                + " | shieldProjectile=" + getEntityUuid(store, shieldProjectileRef)
                + " | mjolnirProjectile=" + getEntityUuid(store, mjolnirProjectileRef));
        return true;
    }

    public static boolean resolveMjolnirProjectileClashImpact(Store<EntityStore> store,
                                                              Ref<EntityStore> shieldProjectileRef,
                                                              Vector3d recoilVelocity,
                                                              long nowMs) {
        TrackedProjectileHandle handle = findTrackedProjectileHandle(store, shieldProjectileRef);
        if (handle == null || handle.trackedState == null) {
            return false;
        }

        handle.trackedState.beginProjectileClashGroundDrop(nowMs);
        handle.trackedState.chainHitCount = 0;
        handle.trackedState.wallBounceCount = 0;
        handle.trackedState.hitTargetUuids.clear();
        handle.trackedState.ownerContactGraceUntilMs = Long.MAX_VALUE;
        clearReturnKickWindow(handle.ownerState);
        applyProjectileClashRecoil(store, shieldProjectileRef, recoilVelocity);
        debug("mjolnir clash impact applied | owner=" + handle.ownerState.ownerUuid
                + " | shieldProjectile=" + getEntityUuid(store, shieldProjectileRef)
                + " | recoil=" + recoilVelocity);
        return true;
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
        ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                resolveActiveOrRecentThrownHand(store, state, ownerRef, System.currentTimeMillis());
        if (activeHand == ShieldCapThrownHandResolver.ActiveThrownHand.NONE) {
            return false;
        }

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
        return now <= state.returnKickWindowUntilMs;
    }

    public static boolean isLeftReturnKickWindowActive(UUID ownerUuid, Ref<EntityStore> ownerRef) {
        if (ownerUuid == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        if (!isReturnKickWindowActive(ownerUuid, ownerRef)) {
            return false;
        }

        Store<EntityStore> store = ownerRef.getStore();
        if (store == null) {
            return false;
        }
        if (!isReturnKickAllowedByHeldItems(store, ownerRef)) {
            return false;
        }

        OwnerHomingState state = ACTIVE_OWNER_STATES.get(ownerUuid);
        ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                resolveActiveOrRecentThrownHand(store, state, ownerRef, System.currentTimeMillis());
        return activeHand == ShieldCapThrownHandResolver.ActiveThrownHand.LEFT;
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
        processPendingReturnCallingEffectRequests(store);
        processPendingReturnCallingClearRequests(store);
        processPendingReturnKickRequests(store);
        processPendingThrowKickRelaunches(store);
        processPendingThrowKickMarks(store);
    }

    public static void queueReturnCallingAnimationClear(UUID ownerUuid) {
        if (ownerUuid != null) {
            PENDING_RETURN_CALLING_CLEAR_REQUESTS.put(ownerUuid, System.currentTimeMillis());
        }
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
                    if (context.getChain() == null) {
                        context.execute(rootInteraction);
                    } else {
                        try {
                            context.fork(InteractionType.Primary, context.duplicate(), rootInteraction, false);
                        } catch (Throwable ignored) {
                            context.execute(rootInteraction);
                        }
                    }
                } finally {
                    clearPendingReturnKickRequest(ownerUuid);
                }
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }

    public static void processPendingReturnCallingEffectRequests(Store<EntityStore> store) {
        if (store == null || PENDING_RETURN_CALLING_EFFECT_REQUESTS.isEmpty()) {
            return;
        }

        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(RETURN_CALLING_ROOT_ID);
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
                if (!PENDING_RETURN_CALLING_EFFECT_REQUESTS.containsKey(ownerUuid)) {
                    continue;
                }

                try {
                    InteractionManager interactionManager =
                            commandBuffer.getComponent(playerRefEntity, InteractionModule.get().getInteractionManagerComponent());
                    if (interactionManager == null) {
                        continue;
                    }

                    InteractionContext context =
                            InteractionContext.forInteraction(interactionManager, playerRefEntity, InteractionType.Primary, commandBuffer);
                    interactionManager.tryStartChain(playerRefEntity, commandBuffer, InteractionType.Primary, context, rootInteraction);
                } finally {
                    PENDING_RETURN_CALLING_EFFECT_REQUESTS.remove(ownerUuid);
                }
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }

    public static void processPendingReturnCallingClearRequests(Store<EntityStore> store) {
        if (store == null || PENDING_RETURN_CALLING_CLEAR_REQUESTS.isEmpty()) {
            return;
        }

        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(RETURN_CALLING_CLEAR_ROOT_ID);
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
                Long requestedAt = PENDING_RETURN_CALLING_CLEAR_REQUESTS.get(ownerUuid);
                if (requestedAt == null) {
                    continue;
                }

                try {
                    InteractionManager interactionManager =
                            commandBuffer.getComponent(playerRefEntity, InteractionModule.get().getInteractionManagerComponent());
                    if (interactionManager == null) {
                        continue;
                    }

                    InteractionContext context =
                            InteractionContext.forInteraction(interactionManager, playerRefEntity, InteractionType.Primary, commandBuffer);
                    interactionManager.tryStartChain(playerRefEntity, commandBuffer, InteractionType.Primary, context, rootInteraction);
                } finally {
                    if (System.currentTimeMillis() - requestedAt >= RETURN_CALLING_CLEAR_RETRY_WINDOW_MS) {
                        PENDING_RETURN_CALLING_CLEAR_REQUESTS.remove(ownerUuid, requestedAt);
                    }
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
                    playImpactSound(store, look.getPosition(), THROW_KICK_IMPACT_SOUND_ID);
                    if (projectileRef == null || !projectileRef.isValid()) {
                        projectileRef = resolvePendingThrowKickProjectile(commandBuffer, playerEntityRef, ownerUuid);
                    }
                    if (projectileRef == null || !projectileRef.isValid()) {
                        PENDING_THROW_KICK_MARKS.put(ownerUuid, System.currentTimeMillis());
                        debug("throwKick relaunch could not resolve spawned projectile | owner=" + ownerUuid);
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

    public static void processPendingThrowKickMarks(Store<EntityStore> store) {
        if (store == null || PENDING_THROW_KICK_MARKS.isEmpty()) {
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
                Long pendingAtMs = PENDING_THROW_KICK_MARKS.get(ownerUuid);
                if (pendingAtMs == null) {
                    continue;
                }

                Ref<EntityStore> projectileRef = resolvePendingThrowKickProjectile(commandBuffer, playerEntityRef, ownerUuid);
                if (projectileRef != null && projectileRef.isValid()) {
                    markProjectile(ownerUuid, playerEntityRef, projectileRef, true);
                    PENDING_THROW_KICK_MARKS.remove(ownerUuid);
                    debug("throwKick pending mark resolved | owner=" + ownerUuid
                            + " | projectile=" + projectileRef);
                    continue;
                }

                if (System.currentTimeMillis() - pendingAtMs > PENDING_REF_GRACE_MS) {
                    PENDING_THROW_KICK_MARKS.remove(ownerUuid);
                    debug("throwKick pending mark expired | owner=" + ownerUuid);
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

        Ref<EntityStore> targetRef = normalizeTargetRef(store, context.getTargetEntity());
        if (countHit && ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            ShieldCapCatch.restoreToOwnerAndRemoveProjectile(store, ownerRef, projectileRef);
            scheduleTrackedProjectileRemoval(store, ownerState, projectileRef);
            return;
        }

        long now = System.currentTimeMillis();
        TrackedProjectileState trackedState =
                ownerState.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
        trackedState.recordActivity(now);

        if (trackedState.fallingAfterProjectileClash || trackedState.groundedAfterProjectileClash) {
            if (isGroundedForProjectileClash(physicsProvider)) {
                trackedState.fallingAfterProjectileClash = false;
                trackedState.groundedAfterProjectileClash = true;
                keepProjectileGroundedAfterClash(
                        context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getVelocityComponentType()),
                        physicsProvider
                );
            } else {
                continueProjectileClashGroundDrop(
                        context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getVelocityComponentType()),
                        physicsProvider,
                        trackedState,
                        now
                );
            }
            return;
        }

        if (countHit && trackedState.isReturningToOwner()) {
            trackedState.recordActivity(now);
            trackedState.groundedAfterProjectileClash = false;
            trackedState.fallingAfterProjectileClash = false;

            Velocity projectileVelocity =
                    context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
            if (ownerRef != null && ownerRef.isValid()) {
                TransformComponent projectileTransform =
                        context.getCommandBuffer().getComponent(projectileRef, EntityModule.get().getTransformComponentType());
                TransformComponent ownerTransform =
                        context.getCommandBuffer().getComponent(ownerRef, EntityModule.get().getTransformComponentType());
                if (projectileTransform != null && projectileTransform.getPosition() != null
                        && ownerTransform != null && ownerTransform.getPosition() != null) {
                    Vector3d ownerAimPosition = getTargetAimPosition(ownerTransform);
                    steerProjectileToTarget(
                            projectileRef,
                            projectileTransform,
                            projectileVelocity,
                            physicsProvider,
                            ownerAimPosition,
                            1.0
                    );
                    phaseProjectileTowardTarget(
                            projectileTransform,
                            projectileVelocity,
                            physicsProvider,
                            ownerAimPosition,
                            1.0
                    );
                    passThroughProjectile(store, projectileRef, PASS_THROUGH_UNSTICK_DISTANCE);
                }
            }
            return;
        }

        boolean shouldRebound = countHit;
        if (countHit) {
            registerHitTarget(context.getCommandBuffer().getStore(), projectileRef, trackedState, ownerRef, targetRef);
        } else {
            clearProjectilePhaseHitTargets(store, projectileRef);
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
            if (trackedState.throwKickMode && ownerRef != null && ownerRef.isValid()) {
                clearReturnKickWindow(ownerState);
                unstickProjectileForReturn(store, projectileRef, ownerRef);
            }
        }

        if (trackedState.chainHitCount >= MAX_CHAIN_HITS) {
            clearProjectilePhaseHitTargets(store, projectileRef);
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    now
            );
        }

        if (countHit && !trackedState.throwKickMode) {
            playProjectileImpactFx(store, projectileRef, NORMAL_IMPACT_SOUND_ID, IMPACT_PARTICLE_ID, NORMAL_IMPACT_PARTICLE_SCALE);
        }

        if (shouldRebound) {
            reboundProjectile(store, projectileRef, trackedState);
        } else if (!trackedState.throwKickMode) {
            playProjectileImpactFx(store, projectileRef, NORMAL_IMPACT_SOUND_ID, IMPACT_PARTICLE_ID, NORMAL_IMPACT_PARTICLE_SCALE);
        }
    }

    private static void playThrowKickStickFx(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        playProjectileImpactFx(
                store,
                projectileRef,
                THROW_KICK_IMPACT_SOUND_ID,
                IMPACT_PARTICLE_ID,
                THROW_KICK_IMPACT_PARTICLE_SCALE
        );
    }

    private static void playProjectileImpactFx(Store<EntityStore> store,
                                               Ref<EntityStore> projectileRef,
                                               String soundEventId,
                                               String particleId,
                                               float particleScale) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        Vector3d impactPos = resolveProjectilePosition(store, projectileRef);
        if (impactPos == null) {
            return;
        }

        playImpactSound(store, impactPos, soundEventId);
        Vector3d particlePos = impactPos.clone();
        if (IMPACT_PARTICLE_ID.equals(particleId)) {
            particlePos.y += IMPACT_PARTICLE_Y_OFFSET;
        }
        spawnParticleFx(store, projectileRef, particleId, particleScale, 0.0f, 0.0f, 0.0f, particlePos);
    }

    private static void spawnParticleFx(Store<EntityStore> store,
                                        Ref<EntityStore> projectileRef,
                                        String particleId,
                                        float particleScale,
                                        float pitch,
                                        float yaw,
                                        float roll) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return;
        }

        Vector3d impactPos = resolveProjectilePosition(store, projectileRef);
        if (impactPos == null) {
            return;
        }

        spawnParticleFx(store, projectileRef, particleId, particleScale, pitch, yaw, roll, impactPos);
    }

    private static void spawnParticleFx(Store<EntityStore> store,
                                        Ref<EntityStore> projectileRef,
                                        String particleId,
                                        float particleScale,
                                        float pitch,
                                        float yaw,
                                        float roll,
                                        Vector3d particlePos) {
        if (store == null || projectileRef == null || !projectileRef.isValid() || particlePos == null) {
            return;
        }

        List<Ref<EntityStore>> viewers = getViewerRefs(store);
        if (viewers.isEmpty()) {
            return;
        }

        ParticleUtil.spawnParticleEffect(
                particleId,
                particlePos,
                pitch,
                yaw,
                roll,
                particleScale,
                null,
                viewers,
                store
        );
    }

    private static void playImpactSound(Store<EntityStore> store,
                                        Vector3d impactPos,
                                        String soundEventId) {
        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(soundEventId, SoundEvent.EMPTY_ID);
        if (soundIndex == SoundEvent.EMPTY_ID || store == null || store.getExternalData() == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        PlaySoundEvent3D packet = new PlaySoundEvent3D(
                soundIndex,
                SoundCategory.SFX,
                PositionUtil.toPositionPacket(impactPos),
                IMPACT_SOUND_VOLUME_MODIFIER,
                1.0f
        );
        world.getNotificationHandler().sendPacketIfChunkLoaded(
                packet,
                (int) Math.floor(impactPos.x),
                (int) Math.floor(impactPos.z)
        );
    }

    private static Vector3d resolveProjectilePosition(Store<EntityStore> store,
                                                      Ref<EntityStore> projectileRef) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return null;
        }

        TransformComponent transform =
                store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
        return transform != null && transform.getPosition() != null ? transform.getPosition().clone() : null;
    }

    private static List<Ref<EntityStore>> getViewerRefs(Store<EntityStore> store) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        if (store == null || store.getExternalData() == null) {
            return refs;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return refs;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null) {
                continue;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                refs.add(ref);
            }
        }
        return refs;
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

            Long pendingThrowKickMarkAtMs = PENDING_THROW_KICK_MARKS.get(state.ownerUuid);
            if (pendingThrowKickMarkAtMs != null) {
                Ref<EntityStore> pendingThrowKickProjectile = resolvePendingThrowKickProjectile(store, ownerRef, state.ownerUuid);
                if (pendingThrowKickProjectile != null && pendingThrowKickProjectile.isValid()) {
                    markProjectile(state.ownerUuid, ownerRef, pendingThrowKickProjectile, true);
                    PENDING_THROW_KICK_MARKS.remove(state.ownerUuid);
                    debug("throwKick pending mark resolved in tickWorld | owner=" + state.ownerUuid
                            + " | projectile=" + getEntityUuid(store, pendingThrowKickProjectile));
                } else if (nowMs - pendingThrowKickMarkAtMs > PENDING_REF_GRACE_MS) {
                    PENDING_THROW_KICK_MARKS.remove(state.ownerUuid);
                    debug("throwKick pending mark expired in tickWorld | owner=" + state.ownerUuid);
                }
            }

            restoreLastKnownShieldProjectileRef(store, state, nowMs);
            syncKnownShieldCapProjectiles(store, state, ownerRef, nowMs);
            boolean hasKnownThrowKickProjectile = syncKnownThrowKickProjectiles(store, state, nowMs);
            if (hasKnownThrowKickProjectile) {
                clearReturnKickWindow(state);
            } else {
                pulseReturnWindowReticleIfEligible(store, state, ownerRef, nowMs);
            }

            List<ProjectileSnapshot> ownerProjectiles = collectTrackedProjectiles(state, store, ownerRef, nowMs);
            if (ownerProjectiles.isEmpty()) {
                ShieldCapThrownHandResolver.ActiveThrownHand thrownHand =
                        ShieldCapThrownHandResolver.resolveActiveThrownHand(store, ownerRef);
                boolean ownerLevelAutoReturnDue =
                        !hasKnownThrowKickProjectile
                                && thrownHand != ShieldCapThrownHandResolver.ActiveThrownHand.NONE
                                && state.lastShieldProjectileMarkedAtMs > 0L
                                && nowMs - state.lastShieldProjectileMarkedAtMs >= FLIGHT_RETURN_TIMEOUT_MS
                                && nowMs - state.lastOwnerAutoReturnFallbackAtMs >= OWNER_AUTO_RETURN_RETRY_INTERVAL_MS;
                if (ownerLevelAutoReturnDue) {
                    state.lastOwnerAutoReturnFallbackAtMs = nowMs;
                    debug("owner-level auto return fallback | owner=" + state.ownerUuid
                            + " | hand=" + thrownHand
                            + " | sinceMarkMs=" + (nowMs - state.lastShieldProjectileMarkedAtMs));
                    forceReturnToOwner(state.ownerUuid, ownerRef);
                }
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

                if (handleMjolnirProjectileClash(store, state, projectile, nowMs)) {
                    continue;
                }

                long autoReturnElapsedMs = trackedState.throwKickMode
                        ? Math.max(0L, getThrowKickReturnDeadline(store, projectile.projectileRef, nowMs + FLIGHT_RETURN_TIMEOUT_MS) - nowMs)
                        : nowMs - trackedState.lastActivityAtMs;
                if (trackedState.throwKickMode
                        && !trackedState.isReturningToOwner()
                        && !trackedState.throwKickStuckInBlock
                        && nowMs >= getThrowKickReturnDeadline(store, projectile.projectileRef, nowMs + FLIGHT_RETURN_TIMEOUT_MS)) {
                    debug("throwKick deadline reached | owner=" + state.ownerUuid
                            + " | projectile=" + getEntityUuid(store, projectile.projectileRef)
                            + " | disableAutoReturn=" + trackedState.disableAutoReturn
                            + " | returning=" + trackedState.isReturningToOwner()
                            + " | stuck=" + trackedState.throwKickStuckInBlock);
                }
                if (!trackedState.isReturningToOwner()
                        && !(trackedState.throwKickMode && trackedState.throwKickStuckInBlock)
                        && (trackedState.throwKickMode
                        ? nowMs >= getThrowKickReturnDeadline(store, projectile.projectileRef, nowMs + FLIGHT_RETURN_TIMEOUT_MS)
                        : autoReturnElapsedMs >= FLIGHT_RETURN_TIMEOUT_MS)) {
                    if (!trackedState.disableAutoReturn) {
                        debug("auto return start | owner=" + state.ownerUuid
                                + " | projectile=" + getEntityUuid(store, projectile.projectileRef)
                                + " | throwKick=" + trackedState.throwKickMode
                                + " | elapsedMs=" + autoReturnElapsedMs
                                + " | velocity=" + projectile.velocity.getVelocity());
                        clearProjectilePhaseHitTargets(store, projectile.projectileRef);
                        trackedState.startReturn(
                                trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                                nowMs
                        );
                        if (trackedState.throwKickMode) {
                            markThrowKickReturning(store, projectile.projectileRef);
                            clearThrowKickStuck(store, projectile.projectileRef);
                            clearReturnKickWindow(state);
                            debug("throwKick auto return start | owner=" + state.ownerUuid
                                    + " | projectile=" + getEntityUuid(store, projectile.projectileRef));
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
                    if (ownerDistance <= OWNER_RETURN_CALLING_TRIGGER_DISTANCE
                            && !trackedState.returnCallingAnimationTriggered) {
                        PENDING_RETURN_CALLING_EFFECT_REQUESTS.put(state.ownerUuid, nowMs);
                        trackedState.returnCallingAnimationTriggered = true;
                        trackedState.returnCallingCatchReadyAtMs = nowMs + RETURN_CALLING_ANIMATION_DELAY_MS;
                    }
                    if (ownerDistance <= OWNER_RETURN_CATCH_DISTANCE
                            && (!trackedState.returnCallingAnimationTriggered
                            || nowMs >= trackedState.returnCallingCatchReadyAtMs)) {
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

        if (!isReturnKickAllowedByHeldItems(store, ownerRef)) {
            clearReturnKickWindow(state);
            debug("reticle blocked by held items | owner=" + state.ownerUuid
                    + " | handState=" + describeHeldShieldState(store, ownerRef));
            return;
        }

        ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                resolveActiveOrRecentThrownHand(store, state, ownerRef, nowMs);
        if (activeHand == ShieldCapThrownHandResolver.ActiveThrownHand.NONE) {
            clearReturnKickWindow(state);
            debug("reticle blocked by no active thrown hand | owner=" + state.ownerUuid
                    + " | handState=" + describeHeldShieldState(store, ownerRef));
            return;
        }

        if (hasReturningThrowKickProjectile(store, state)) {
            clearReturnKickWindow(state);
            debug("reticle blocked by returning throwKick | owner=" + state.ownerUuid);
            return;
        }

        if (!hasReturningProjectileInKickRange(store, state, ownerRef, nowMs)) {
            debug("reticle blocked by no returning projectile in range | owner=" + state.ownerUuid
                    + " | hand=" + activeHand
                    + " | handState=" + describeHeldShieldState(store, ownerRef));
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
        state.returnKickWindowUntilMs = nowMs + ShieldCapReturnReticleInjector.getReturnWindowDurationMs();
        debug("reticle pulse sent | owner=" + state.ownerUuid);
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

    private static boolean isReturnKickAllowedByHeldItems(Store<EntityStore> store,
                                                          Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return false;
        }

        Player player = store.getComponent(ownerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return false;
        }

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        byte activeHotbarSlot = inventory.getActiveHotbarSlot();
        ItemStack mainHandStack = isInventorySlotUsable(hotbar, activeHotbarSlot)
                ? hotbar.getItemStack(activeHotbarSlot)
                : null;

        if (matchesItem(mainHandStack, ShieldCapThrownHandResolver.THROWN_ITEM_ID)) {
            return true;
        }
        if (matchesItem(mainHandStack, MJOLNIR_ITEM_ID)) {
            return true;
        }

        if (!isEmptyItem(mainHandStack)) {
            return false;
        }

        ItemContainer utility = inventory.getUtility();
        byte activeUtilitySlot = inventory.getActiveUtilitySlot();
        return isInventorySlotUsable(utility, activeUtilitySlot)
                && matchesItem(utility.getItemStack(activeUtilitySlot), ShieldCapThrownHandResolver.THROWN_ITEM_ID);
    }

    private static boolean handleMjolnirProjectileClash(Store<EntityStore> store,
                                                        OwnerHomingState state,
                                                        ProjectileSnapshot projectile,
                                                        long nowMs) {
        if (store == null || state == null || projectile == null || projectile.trackedState == null) {
            return false;
        }

        TrackedProjectileState trackedState = projectile.trackedState;
        if (trackedState.throwKickMode) {
            trackedState.clearMjolnirProjectileClash();
            return false;
        }

        if (trackedState.groundedAfterProjectileClash) {
            keepProjectileGroundedAfterClash(projectile.velocity, projectile.physicsProvider);
            return true;
        }

        if (trackedState.fallingAfterProjectileClash) {
            continueProjectileClashGroundDrop(projectile.velocity, projectile.physicsProvider, trackedState, nowMs);
            return true;
        }

        Ref<EntityStore> mjolnirProjectileRef = trackedState.mjolnirClashTargetRef;
        if (mjolnirProjectileRef == null || !isMjolnirProjectileCandidate(store, mjolnirProjectileRef)) {
            trackedState.clearMjolnirProjectileClash();
            return false;
        }
        if (isMjolnirProjectileReturningForClash(store, mjolnirProjectileRef)) {
            trackedState.clearMjolnirProjectileClash();
            return false;
        }

        TransformComponent mjolnirTransform =
                store.getComponent(mjolnirProjectileRef, EntityModule.get().getTransformComponentType());
        if (mjolnirTransform == null || mjolnirTransform.getPosition() == null) {
            trackedState.clearMjolnirProjectileClash();
            return false;
        }

        Vector3d mjolnirPosition = mjolnirTransform.getPosition();
        if (projectile.transform.getPosition().distanceTo(mjolnirPosition) <= PROJECTILE_CLASH_TOUCH_DISTANCE) {
            forceMjolnirProjectileClashImpact(store, mjolnirProjectileRef, projectile.projectileRef, nowMs);
            resolveMjolnirProjectileClashImpact(
                    store,
                    projectile.projectileRef,
                    buildProjectileClashRecoil(
                            store.getComponent(mjolnirProjectileRef, EntityModule.get().getVelocityComponentType())
                    ),
                    nowMs
            );
            return true;
        }

        trackedState.recordActivity(nowMs);
        clearReturnKickWindow(state);
        steerProjectileToTarget(
                projectile.projectileRef,
                projectile.transform,
                projectile.velocity,
                projectile.physicsProvider,
                mjolnirPosition,
                1.0
        );
        phaseProjectileTowardTarget(
                projectile.transform,
                projectile.velocity,
                projectile.physicsProvider,
                mjolnirPosition,
                1.0
        );
        return true;
    }

    private static ShieldCapThrownHandResolver.ActiveThrownHand resolveActiveOrRecentThrownHand(Store<EntityStore> store,
                                                                                                OwnerHomingState state,
                                                                                                Ref<EntityStore> ownerRef,
                                                                                                long nowMs) {
        ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                ShieldCapThrownHandResolver.resolveActiveThrownHand(store, ownerRef);
        if (activeHand != ShieldCapThrownHandResolver.ActiveThrownHand.NONE) {
            if (state != null) {
                state.lastObservedThrownHand = activeHand;
                state.lastObservedThrownHandAtMs = nowMs;
            }
            return activeHand;
        }
        if (state != null
                && state.lastObservedThrownHand != null
                && state.lastObservedThrownHand != ShieldCapThrownHandResolver.ActiveThrownHand.NONE
                && (nowMs - state.lastObservedThrownHandAtMs <= RECENT_THROWN_HAND_GRACE_MS
                || hasTrackedNormalShieldProjectile(state))) {
            return state.lastObservedThrownHand;
        }
        return ShieldCapThrownHandResolver.ActiveThrownHand.NONE;
    }

    private static boolean hasTrackedNormalShieldProjectile(OwnerHomingState state) {
        if (state == null) {
            return false;
        }

        for (TrackedProjectileState trackedState : state.trackedProjectiles.values()) {
            if (trackedState != null && !trackedState.throwKickMode) {
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
            if ((!trackedState.throwKickMode && !isKnownThrowKickProjectile(store, projectileRef))
                    || (!trackedState.isReturningToOwner() && !isReturningThrowKickProjectile(store, projectileRef))) {
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
                                          Ref<EntityStore> projectileRef,
                                          TrackedProjectileState trackedState,
                                          Ref<EntityStore> ownerRef,
                                          Ref<EntityStore> targetRef) {
        if (trackedState == null || targetRef == null || !targetRef.isValid()) {
            return;
        }
        if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(targetRef)) {
            clearProjectilePhaseHitTargets(store, projectileRef);
            trackedState.startReturn(
                    trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                    System.currentTimeMillis()
            );
            return;
        }
        if (isProjectileEntity(store, targetRef)) {
            return;
        }

        trackedState.hitTargetRefs.add(targetRef);
        UUID targetUuid = getEntityUuid(store, targetRef);
        rememberProjectilePhaseHitTarget(store, projectileRef, targetRef, targetUuid);
        if (targetUuid != null && trackedState.hitTargetUuids.add(targetUuid)) {
            trackedState.chainHitCount++;
            return;
        }
        if (targetUuid == null) {
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
        passThroughProjectile(store, projectileRef, PASS_THROUGH_UNSTICK_DISTANCE);
    }

    private static void passThroughProjectile(Store<EntityStore> store,
                                              Ref<EntityStore> projectileRef,
                                              double unstuckDistance) {
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
                projectileTransform.getPosition().clone().add(forward.scale(Math.max(IMPACT_UNSTICK_DISTANCE, unstuckDistance)))
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
            double reflectedSpeed = Math.max(MIN_PROJECTILE_SPEED, currentVelocity.length() * NORMAL_BLOCK_BOUNCE_SPEED_MULTIPLIER);
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
                if (!hasExplicitShieldCapIdentity(commandBuffer.getStore(), projectileRef)
                        && !projectileRef.equals(state.lastKnownShieldProjectileRef)) {
                    continue;
                }

                TrackedProjectileState trackedState =
                        state.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(now));
                if (isKnownThrowKickProjectile(store, projectileRef)) {
                    armThrowKickReturnTimeout(store, projectileRef, now);
                    trackedState.markAsThrowKick(now);
                }
                clearProjectilePhaseHitTargets(store, projectileRef);
                trackedState.startReturn(
                        trackedState.throwKickMode ? ReturnMode.THROW_KICK : ReturnMode.NORMAL,
                        now
                );
                if (trackedState.throwKickMode) {
                    markThrowKickReturning(store, projectileRef);
                    clearThrowKickStuck(store, projectileRef);
                    clearReturnKickWindow(state);
                    debug("throwKick recovered and forced to return | owner=" + state.ownerUuid
                            + " | projectile=" + getEntityUuid(store, projectileRef));
                }
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

    private static Ref<EntityStore> resolveNearbyOwnerProjectile(CommandBuffer<EntityStore> commandBuffer,
                                                                 Ref<EntityStore> ownerRef,
                                                                 UUID ownerUuid) {
        if (commandBuffer == null || ownerRef == null || !ownerRef.isValid() || ownerUuid == null) {
            return null;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        if (store == null) {
            return null;
        }

        TransformComponent ownerTransform =
                commandBuffer.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        if (ownerTransform == null || ownerTransform.getPosition() == null) {
            return null;
        }

        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                store.getResource(EntityModule.get().getEntitySpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return null;
        }

        Vector3d ownerPos = ownerTransform.getPosition();
        List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
        refs.clear();
        spatial.getSpatialStructure().collectCylinder(ownerPos, THROW_KICK_SPAWN_RESOLVE_RANGE, THROW_KICK_SPAWN_RESOLVE_HEIGHT, refs);

        Ref<EntityStore> bestRef = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid() || ref.equals(ownerRef)) {
                continue;
            }
            if (store.getComponent(ref, Player.getComponentType()) != null) {
                continue;
            }

            StandardPhysicsProvider physicsProvider =
                    commandBuffer.getComponent(ref, ProjectileModule.get().getStandardPhysicsProviderComponentType());
            if (physicsProvider == null) {
                continue;
            }

            UUID creatorUuid = physicsProvider.getCreatorUuid();
            if (creatorUuid == null || !creatorUuid.equals(ownerUuid)) {
                continue;
            }
            if (!isShieldCapProjectile(commandBuffer, ref)) {
                continue;
            }

            Velocity velocity = commandBuffer.getComponent(ref, EntityModule.get().getVelocityComponentType());
            if (velocity == null || velocity.getVelocity() == null || velocity.getVelocity().length() < THROW_KICK_SPAWN_MIN_SPEED) {
                continue;
            }

            TransformComponent projectileTransform =
                    commandBuffer.getComponent(ref, EntityModule.get().getTransformComponentType());
            if (projectileTransform == null || projectileTransform.getPosition() == null) {
                continue;
            }

            double distanceSq = ownerPos.distanceSquaredTo(projectileTransform.getPosition());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestRef = ref;
            }
        }

        refs.clear();
        return bestRef;
    }

    private static Ref<EntityStore> resolvePendingThrowKickProjectile(Store<EntityStore> store,
                                                                      Ref<EntityStore> ownerRef,
                                                                      UUID ownerUuid) {
        if (store == null || ownerUuid == null) {
            return null;
        }

        final Ref<EntityStore>[] bestRef = new Ref[]{null};
        final double[] bestScore = new double[]{Double.NEGATIVE_INFINITY};
        TransformComponent ownerTransform =
                ownerRef == null || !ownerRef.isValid()
                        ? null
                        : store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        Vector3d ownerPos = ownerTransform == null ? null : ownerTransform.getPosition();

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, cb) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> projectileRef = chunk.getReferenceTo(index);
                if (projectileRef == null || !projectileRef.isValid()) {
                    continue;
                }
                if (ownerRef != null && ownerRef.isValid() && ownerRef.equals(projectileRef)) {
                    continue;
                }
                if (cb.getComponent(projectileRef, Player.getComponentType()) != null) {
                    continue;
                }

                StandardPhysicsProvider physicsProvider =
                        cb.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
                if (physicsProvider == null) {
                    continue;
                }
                UUID creatorUuid = physicsProvider.getCreatorUuid();
                if (creatorUuid == null || !creatorUuid.equals(ownerUuid)) {
                    continue;
                }
                if (!isShieldCapProjectile(store, projectileRef)) {
                    continue;
                }

                Velocity velocity = cb.getComponent(projectileRef, EntityModule.get().getVelocityComponentType());
                TransformComponent projectileTransform =
                        cb.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
                if (velocity == null || velocity.getVelocity() == null
                        || projectileTransform == null || projectileTransform.getPosition() == null) {
                    continue;
                }

                double speed = velocity.getVelocity().length();
                if (!Double.isFinite(speed) || speed < THROW_KICK_SPAWN_MIN_SPEED) {
                    continue;
                }

                double score = speed;
                if (ownerPos != null) {
                    double distance = ownerPos.distanceTo(projectileTransform.getPosition());
                    if (Double.isFinite(distance)) {
                        score -= distance;
                    }
                }

                if (score > bestScore[0]) {
                    bestScore[0] = score;
                    bestRef[0] = projectileRef;
                }
            }
        };
        store.forEachChunk(ProjectileModule.get().getStandardPhysicsProviderComponentType(), chunkConsumer);
        return bestRef[0];
    }

    private static Ref<EntityStore> resolvePendingThrowKickProjectile(CommandBuffer<EntityStore> commandBuffer,
                                                                      Ref<EntityStore> ownerRef,
                                                                      UUID ownerUuid) {
        Ref<EntityStore> nearbyRef = resolveNearbyOwnerProjectile(commandBuffer, ownerRef, ownerUuid);
        if (nearbyRef != null && nearbyRef.isValid()) {
            return nearbyRef;
        }

        if (commandBuffer == null || ownerUuid == null) {
            return null;
        }
        return resolvePendingThrowKickProjectile(commandBuffer.getStore(), ownerRef, ownerUuid);
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
            if (!isShieldCapProjectile(store, projectileRef)) {
                iterator.remove();
                continue;
            }

            if (projectileTransform == null || projectileTransform.getPosition() == null || projectileVelocity == null) {
                iterator.remove();
                continue;
            }

            if (isKnownThrowKickProjectile(store, projectileRef) && !trackedState.throwKickMode) {
                armThrowKickReturnTimeout(store, projectileRef, nowMs);
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
                long trackedAgeMs = nowMs - trackedState.lastActivityAtMs;
                if (trackedAgeMs < FLIGHT_RETURN_TIMEOUT_MS) {
                    debug("tracked projectile retained despite low speed | owner=" + state.ownerUuid
                            + " | projectile=" + getEntityUuid(store, projectileRef)
                            + " | speed=" + velocityVector.length()
                            + " | trackedAgeMs=" + trackedAgeMs);
                    snapshots.add(new ProjectileSnapshot(projectileRef, projectileTransform, projectileVelocity, physicsProvider, trackedState));
                    continue;
                }
                debug("tracked projectile removed after stale low speed | owner=" + state.ownerUuid
                        + " | projectile=" + getEntityUuid(store, projectileRef)
                        + " | speed=" + velocityVector.length()
                        + " | trackedAgeMs=" + trackedAgeMs);
            }

            if (!trackedState.throwKickMode) {
                state.lastKnownShieldProjectileRef = projectileRef;
                state.lastKnownShieldProjectilePosition = projectileTransform.getPosition().clone();
                state.lastKnownShieldProjectileSeenAtMs = nowMs;
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

    private static boolean hasExplicitShieldCapIdentity(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }

        UUID projectileUuid = getEntityUuid(store, ref);
        if ((projectileUuid != null && KNOWN_SHIELDCAP_PROJECTILE_UUIDS.contains(projectileUuid))
                || (projectileUuid != null && KNOWN_THROW_KICK_PROJECTILE_UUIDS.contains(projectileUuid))) {
            return true;
        }

        ProjectileComponent projectileComponent = store.getComponent(ref, ProjectileComponent.getComponentType());
        if (projectileComponent != null) {
            String projectileAssetName = projectileComponent.getProjectileAssetName();
            if (SHIELDCAP_PROJECTILE_ASSET_ID.equals(projectileAssetName)) {
                return true;
            }
            if (projectileAssetName != null && !projectileAssetName.isBlank()) {
                return false;
            }
        }

        return false;
    }

    private static boolean isRememberedShieldCapProjectileRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }

        return KNOWN_SHIELDCAP_PROJECTILE_REFS.contains(ref)
                || KNOWN_THROW_KICK_PROJECTILE_REFS.contains(ref);
    }

    private static boolean isShieldCapProjectile(Store<EntityStore> store, Ref<EntityStore> ref) {
        return hasExplicitShieldCapIdentity(store, ref)
                || isRememberedShieldCapProjectileRef(store, ref);
    }

    private static boolean isShieldCapProjectile(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref) {
        return commandBuffer != null && isShieldCapProjectile(commandBuffer.getStore(), ref);
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

    private static boolean isMjolnirProjectileReturningForClash(Store<EntityStore> store,
                                                                Ref<EntityStore> projectileRef) {
        if (store == null || projectileRef == null || !projectileRef.isValid() || !ensureMjolnirClashBridgeLoaded()) {
            return false;
        }

        try {
            Object result = mjolnirProjectileReturningForClashMethod.invoke(null, store, projectileRef);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MJOLNIR_CLASH_BRIDGE_AVAILABLE = false;
            debug("mjolnir clash bridge return-state failed | reason=" + exception.getClass().getSimpleName()
                    + " | message=" + exception.getMessage());
            return false;
        }
    }

    private static boolean ensureMjolnirClashBridgeLoaded() {
        if (MJOLNIR_CLASH_BRIDGE_LOOKUP_ATTEMPTED) {
            return MJOLNIR_CLASH_BRIDGE_AVAILABLE
                    && mjolnirProjectileReturningForClashMethod != null
                    && mjolnirForceProjectileClashImpactMethod != null;
        }

        MJOLNIR_CLASH_BRIDGE_LOOKUP_ATTEMPTED = true;
        try {
            Class<?> mjolnirServiceClass = loadMjolnirClashBridgeClass();
            mjolnirProjectileReturningForClashMethod = mjolnirServiceClass.getMethod(
                    "isProjectileReturningToOwnerForShieldCapClash",
                    Store.class,
                    Ref.class
            );
            mjolnirForceProjectileClashImpactMethod = mjolnirServiceClass.getMethod(
                    "forceShieldCapProjectileClashImpact",
                    Store.class,
                    Ref.class,
                    Ref.class,
                    long.class
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MJOLNIR_CLASH_BRIDGE_AVAILABLE = false;
            debug("mjolnir clash bridge unavailable | reason=" + exception.getClass().getSimpleName()
                    + " | message=" + exception.getMessage());
        }

        return MJOLNIR_CLASH_BRIDGE_AVAILABLE
                && mjolnirProjectileReturningForClashMethod != null
                && mjolnirForceProjectileClashImpactMethod != null;
    }

    private static boolean forceMjolnirProjectileClashImpact(Store<EntityStore> store,
                                                             Ref<EntityStore> mjolnirProjectileRef,
                                                             Ref<EntityStore> shieldProjectileRef,
                                                             long nowMs) {
        if (store == null || mjolnirProjectileRef == null || shieldProjectileRef == null || !ensureMjolnirClashBridgeLoaded()) {
            return false;
        }

        try {
            Object result = mjolnirForceProjectileClashImpactMethod.invoke(
                    null,
                    store,
                    mjolnirProjectileRef,
                    shieldProjectileRef,
                    nowMs
            );
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MJOLNIR_CLASH_BRIDGE_AVAILABLE = false;
            debug("mjolnir clash bridge force-impact failed | reason=" + exception.getClass().getSimpleName()
                    + " | message=" + exception.getMessage());
            return false;
        }
    }

    private static Class<?> loadMjolnirClashBridgeClass() throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(
                        "co.carrd.starkymods.interactions.MjolnirThrowHomingServiceAlt",
                        true,
                        contextClassLoader
                );
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(
                "co.carrd.starkymods.interactions.MjolnirThrowHomingServiceAlt",
                true,
                ShieldCapThrowHomingService.class.getClassLoader()
        );
    }

    private static TrackedProjectileHandle findTrackedProjectileHandle(Store<EntityStore> store,
                                                                       Ref<EntityStore> projectileRef) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return null;
        }

        for (OwnerHomingState state : ACTIVE_OWNER_STATES.values()) {
            if (state == null || (state.armedStore != null && state.armedStore != store)) {
                continue;
            }

            TrackedProjectileState trackedState = state.trackedProjectiles.get(projectileRef);
            if (trackedState != null) {
                return new TrackedProjectileHandle(state, trackedState);
            }
        }

        return null;
    }

    private static boolean isEligibleForMjolnirProjectileClash(Store<EntityStore> store,
                                                               Ref<EntityStore> projectileRef,
                                                               TrackedProjectileState trackedState,
                                                               Ref<EntityStore> mjolnirProjectileRef) {
        if (store == null || projectileRef == null || trackedState == null || !projectileRef.isValid()) {
            return false;
        }
        if (projectileRef.getStore() != store || !isShieldCapProjectile(store, projectileRef) || trackedState.throwKickMode) {
            return false;
        }
        if (trackedState.fallingAfterProjectileClash || trackedState.groundedAfterProjectileClash) {
            return false;
        }
        if (mjolnirProjectileRef != null && isMjolnirProjectileReturningForClash(store, mjolnirProjectileRef)) {
            return false;
        }

        Ref<EntityStore> currentTarget = trackedState.mjolnirClashTargetRef;
        return currentTarget == null || !currentTarget.isValid() || currentTarget.equals(mjolnirProjectileRef);
    }

    private static boolean isMjolnirProjectileCandidate(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        if (store == null || projectileRef == null || !projectileRef.isValid() || projectileRef.getStore() != store) {
            return false;
        }

        ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
        if (projectileComponent == null) {
            return false;
        }

        String projectileAssetName = projectileComponent.getProjectileAssetName();
        return MJOLNIR_PROJECTILE_ASSET_ID.equals(projectileAssetName);
    }

    private static void applyProjectileClashRecoil(Store<EntityStore> store,
                                                   Ref<EntityStore> projectileRef,
                                                   Vector3d recoilVelocity) {
        if (store == null || projectileRef == null || !projectileRef.isValid() || recoilVelocity == null || !recoilVelocity.isFinite()) {
            return;
        }

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

        Vector3d normalizedRecoil = recoilVelocity.clone();
        if (normalizedRecoil.closeToZero(1e-6)) {
            normalizedRecoil = new Vector3d(0.0, 0.0, -1.0);
        } else {
            normalizedRecoil.normalize();
        }

        Vector3d launchVelocity = recoilVelocity.clone();
        launchVelocity.y = Math.min(launchVelocity.y, -PROJECTILE_CLASH_GROUND_FALL_SPEED);
        projectileVelocity.set(launchVelocity);
        projectileTransform.setPosition(
                projectileTransform.getPosition().clone().add(normalizedRecoil.scale(IMPACT_UNSTICK_DISTANCE))
        );

        if (physicsProvider.getPosition() != null) {
            physicsProvider.getPosition().assign(projectileTransform.getPosition());
        }
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(launchVelocity);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(launchVelocity.clone().scale(WORLD_TICK_INTERVAL_MS / 1000.0));
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(launchVelocity.clone().scale(WORLD_TICK_INTERVAL_MS / 1000.0));
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
    }

    private static Vector3d buildProjectileClashRecoil(Velocity projectileVelocity) {
        Vector3d currentVelocity = projectileVelocity == null ? null : projectileVelocity.getVelocity();
        Vector3d recoilDirection =
                currentVelocity == null || !currentVelocity.isFinite() || currentVelocity.closeToZero(1e-6)
                        ? new Vector3d(0.0, 0.0, -1.0)
                        : currentVelocity.clone().normalize().scale(-1.0);
        recoilDirection.y -= 0.6;
        if (!recoilDirection.isFinite() || recoilDirection.closeToZero(1e-6)) {
            recoilDirection = new Vector3d(0.0, -0.6, -1.0);
        }
        recoilDirection.normalize();
        return recoilDirection.scale(PROJECTILE_CLASH_GROUND_FALL_SPEED);
    }

    private static void continueProjectileClashGroundDrop(Velocity projectileVelocity,
                                                          StandardPhysicsProvider physicsProvider,
                                                          TrackedProjectileState trackedState,
                                                          long nowMs) {
        if (trackedState == null || physicsProvider == null) {
            return;
        }

        if (isGroundedForProjectileClash(physicsProvider)) {
            trackedState.fallingAfterProjectileClash = false;
            trackedState.groundedAfterProjectileClash = true;
            trackedState.lastActivityAtMs = nowMs;
            keepProjectileGroundedAfterClash(projectileVelocity, physicsProvider);
            return;
        }

        Vector3d clashFallVelocity = projectileVelocity == null ? null : projectileVelocity.getVelocity();
        double vx = 0.0;
        double vz = 0.0;
        if (clashFallVelocity != null && clashFallVelocity.isFinite()) {
            vx = clashFallVelocity.x * PROJECTILE_CLASH_GROUND_DRIFT_MULTIPLIER;
            vz = clashFallVelocity.z * PROJECTILE_CLASH_GROUND_DRIFT_MULTIPLIER;
        }
        Vector3d assistedVelocity = new Vector3d(vx, -PROJECTILE_CLASH_GROUND_FALL_SPEED, vz);
        if (projectileVelocity != null) {
            projectileVelocity.set(assistedVelocity);
        }
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(assistedVelocity);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(assistedVelocity.clone().scale(WORLD_TICK_INTERVAL_MS / 1000.0));
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(assistedVelocity.clone().scale(WORLD_TICK_INTERVAL_MS / 1000.0));
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(false);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
        trackedState.lastActivityAtMs = nowMs;
    }

    private static boolean isGroundedForProjectileClash(StandardPhysicsProvider physicsProvider) {
        if (physicsProvider == null) {
            return false;
        }

        Vector3d contactNormal = physicsProvider.getContactNormal();
        return physicsProvider.isOnGround()
                || (contactNormal != null && contactNormal.isFinite() && contactNormal.y > 0.55);
    }

    private static void keepProjectileGroundedAfterClash(Velocity projectileVelocity,
                                                         StandardPhysicsProvider physicsProvider) {
        if (projectileVelocity != null) {
            projectileVelocity.set(0.0, 0.0, 0.0);
        }

        if (physicsProvider == null) {
            return;
        }

        Vector3d zero = new Vector3d(0.0, 0.0, 0.0);
        if (physicsProvider.getVelocity() != null) {
            physicsProvider.getVelocity().assign(zero);
        }
        if (physicsProvider.getMovement() != null) {
            physicsProvider.getMovement().assign(zero);
        }
        if (physicsProvider.getNextMovement() != null) {
            physicsProvider.getNextMovement().assign(zero);
        }
        physicsProvider.setState(StandardPhysicsProvider.STATE.ACTIVE);
        physicsProvider.setOnGround(true);
        physicsProvider.setSliding(false);
        physicsProvider.setBounced(false);
        physicsProvider.setMovedInsideSolid(false);
        physicsProvider.setVelocityExtremaCount(0);
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

    private static Vector3d getEntityPosition(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return null;
        }

        TransformComponent transform = store.getComponent(ref, EntityModule.get().getTransformComponentType());
        if (transform == null || transform.getPosition() == null || !transform.getPosition().isFinite()) {
            return null;
        }

        return transform.getPosition().clone();
    }

    private static Ref<EntityStore> normalizeTargetRef(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null || !targetRef.isValid()) {
            return targetRef;
        }

        Ref<EntityStore> currentRef = targetRef;
        for (int depth = 0; depth < TARGET_ROOT_RESOLVE_MAX_DEPTH; depth++) {
            if (currentRef == null || !currentRef.isValid()) {
                return targetRef;
            }
            if (store.getComponent(currentRef, Player.getComponentType()) != null) {
                return currentRef;
            }

            MountedComponent mountedComponent = store.getComponent(currentRef, MountedComponent.getComponentType());
            if (mountedComponent == null || mountedComponent.getMountedToEntity() == null) {
                return currentRef;
            }

            Ref<EntityStore> mountedToEntity = mountedComponent.getMountedToEntity();
            if (mountedToEntity == null || !mountedToEntity.isValid() || mountedToEntity.equals(currentRef)) {
                return currentRef;
            }
            currentRef = mountedToEntity;
        }

        return currentRef;
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
        forgetShieldCapProjectile(store, projectileRef);
        forgetThrowKickProjectile(store, projectileRef);
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        ShieldCapSafeRemoveProjectile.scheduleSafeRemoval(store, projectileRef, projectileUuid, OWNER_RETURN_REMOVE_DELAY_MS);
    }

    private static void rememberShieldCapProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_SHIELDCAP_PROJECTILE_UUIDS.add(projectileUuid);
        }
        if (projectileRef != null) {
            KNOWN_SHIELDCAP_PROJECTILE_REFS.add(projectileRef);
        }
    }

    private static void forgetShieldCapProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        clearProjectilePhaseHitTargets(store, projectileRef);
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_SHIELDCAP_PROJECTILE_UUIDS.remove(projectileUuid);
        }
        if (projectileRef != null) {
            KNOWN_SHIELDCAP_PROJECTILE_REFS.remove(projectileRef);
        }
    }

    private static void rememberThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_THROW_KICK_PROJECTILE_UUIDS.add(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            KNOWN_THROW_KICK_PROJECTILE_REFS.add(projectileRef);
        }
    }

    private static void forgetThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        clearProjectilePhaseHitTargets(store, projectileRef);
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            KNOWN_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            THROW_KICK_RETURN_DEADLINES.remove(projectileUuid);
            RETURNING_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            STUCK_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            KNOWN_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
            THROW_KICK_RETURN_DEADLINES_BY_REF.remove(projectileRef);
            RETURNING_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
            STUCK_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
        }
    }

    private static boolean isKnownThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            return KNOWN_THROW_KICK_PROJECTILE_UUIDS.contains(projectileUuid);
        }
        return projectileRef != null && KNOWN_THROW_KICK_PROJECTILE_REFS.contains(projectileRef);
    }

    private static boolean hasProjectilePhaseHitTarget(Store<EntityStore> store,
                                                       Ref<EntityStore> projectileRef,
                                                       Ref<EntityStore> targetRef,
                                                       UUID targetUuid) {
        if (projectileRef == null || targetRef == null) {
            return false;
        }

        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            Set<UUID> hitUuids = PROJECTILE_PHASE_HIT_TARGET_UUIDS.get(projectileUuid);
            if (targetUuid != null && hitUuids != null && hitUuids.contains(targetUuid)) {
                return true;
            }
            Set<Ref<EntityStore>> hitRefs = PROJECTILE_PHASE_HIT_TARGET_REFS.get(projectileUuid);
            if (hitRefs != null && hitRefs.contains(targetRef)) {
                return true;
            }
            return isCloseToRememberedTargetPosition(
                    PROJECTILE_PHASE_HIT_TARGET_POSITIONS.get(projectileUuid),
                    getEntityPosition(store, targetRef)
            );
        }

        Set<UUID> hitUuids = PROJECTILE_PHASE_HIT_TARGET_UUIDS_BY_REF.get(projectileRef);
        if (targetUuid != null && hitUuids != null && hitUuids.contains(targetUuid)) {
            return true;
        }
        Set<Ref<EntityStore>> hitRefs = PROJECTILE_PHASE_HIT_TARGET_REFS_BY_REF.get(projectileRef);
        if (hitRefs != null && hitRefs.contains(targetRef)) {
            return true;
        }
        return isCloseToRememberedTargetPosition(
                PROJECTILE_PHASE_HIT_TARGET_POSITIONS_BY_REF.get(projectileRef),
                getEntityPosition(store, targetRef)
        );
    }

    private static void rememberProjectilePhaseHitTarget(Store<EntityStore> store,
                                                         Ref<EntityStore> projectileRef,
                                                         Ref<EntityStore> targetRef,
                                                         UUID targetUuid) {
        if (projectileRef == null || targetRef == null) {
            return;
        }

        Vector3d targetPosition = getEntityPosition(store, targetRef);
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            PROJECTILE_PHASE_HIT_TARGET_REFS
                    .computeIfAbsent(projectileUuid, ignored -> ConcurrentHashMap.newKeySet())
                    .add(targetRef);
            if (targetUuid != null) {
                PROJECTILE_PHASE_HIT_TARGET_UUIDS
                        .computeIfAbsent(projectileUuid, ignored -> ConcurrentHashMap.newKeySet())
                        .add(targetUuid);
            }
            if (targetPosition != null) {
                PROJECTILE_PHASE_HIT_TARGET_POSITIONS
                        .computeIfAbsent(projectileUuid, ignored -> new CopyOnWriteArrayList<>())
                        .add(targetPosition);
            }
            return;
        }

        PROJECTILE_PHASE_HIT_TARGET_REFS_BY_REF
                .computeIfAbsent(projectileRef, ignored -> ConcurrentHashMap.newKeySet())
                .add(targetRef);
        if (targetUuid != null) {
            PROJECTILE_PHASE_HIT_TARGET_UUIDS_BY_REF
                    .computeIfAbsent(projectileRef, ignored -> ConcurrentHashMap.newKeySet())
                    .add(targetUuid);
        }
        if (targetPosition != null) {
            PROJECTILE_PHASE_HIT_TARGET_POSITIONS_BY_REF
                    .computeIfAbsent(projectileRef, ignored -> new CopyOnWriteArrayList<>())
                    .add(targetPosition);
        }
    }

    private static void clearProjectilePhaseHitTargets(Store<EntityStore> store,
                                                       Ref<EntityStore> projectileRef) {
        if (projectileRef == null) {
            return;
        }

        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            PROJECTILE_PHASE_HIT_TARGET_UUIDS.remove(projectileUuid);
            PROJECTILE_PHASE_HIT_TARGET_REFS.remove(projectileUuid);
            PROJECTILE_PHASE_HIT_TARGET_POSITIONS.remove(projectileUuid);
        }
        PROJECTILE_PHASE_HIT_TARGET_UUIDS_BY_REF.remove(projectileRef);
        PROJECTILE_PHASE_HIT_TARGET_REFS_BY_REF.remove(projectileRef);
        PROJECTILE_PHASE_HIT_TARGET_POSITIONS_BY_REF.remove(projectileRef);
    }

    private static boolean isCloseToRememberedTargetPosition(List<Vector3d> rememberedPositions,
                                                             Vector3d targetPosition) {
        if (rememberedPositions == null || rememberedPositions.isEmpty()
                || targetPosition == null || !targetPosition.isFinite()) {
            return false;
        }

        double toleranceSq = SAME_TARGET_POSITION_TOLERANCE * SAME_TARGET_POSITION_TOLERANCE;
        for (Vector3d rememberedPosition : rememberedPositions) {
            if (rememberedPosition == null || !rememberedPosition.isFinite()) {
                continue;
            }
            if (rememberedPosition.distanceSquaredTo(targetPosition) <= toleranceSq) {
                return true;
            }
        }
        return false;
    }

    private static void armThrowKickReturnTimeout(Store<EntityStore> store, Ref<EntityStore> projectileRef, long nowMs) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            THROW_KICK_RETURN_DEADLINES.putIfAbsent(projectileUuid, nowMs + FLIGHT_RETURN_TIMEOUT_MS);
            return;
        }
        if (projectileRef != null) {
            THROW_KICK_RETURN_DEADLINES_BY_REF.putIfAbsent(projectileRef, nowMs + FLIGHT_RETURN_TIMEOUT_MS);
        }
    }

    private static void resetThrowKickReturnTimeout(Store<EntityStore> store, Ref<EntityStore> projectileRef, long nowMs) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            THROW_KICK_RETURN_DEADLINES.put(projectileUuid, nowMs + FLIGHT_RETURN_TIMEOUT_MS);
            RETURNING_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            THROW_KICK_RETURN_DEADLINES_BY_REF.put(projectileRef, nowMs + FLIGHT_RETURN_TIMEOUT_MS);
            RETURNING_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
        }
    }

    private static long getThrowKickReturnDeadline(Store<EntityStore> store, Ref<EntityStore> projectileRef, long fallbackDeadlineMs) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            return THROW_KICK_RETURN_DEADLINES.getOrDefault(projectileUuid, fallbackDeadlineMs);
        }
        if (projectileRef != null) {
            return THROW_KICK_RETURN_DEADLINES_BY_REF.getOrDefault(projectileRef, fallbackDeadlineMs);
        }
        return fallbackDeadlineMs;
    }

    private static void markThrowKickReturning(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            RETURNING_THROW_KICK_PROJECTILE_UUIDS.add(projectileUuid);
            THROW_KICK_RETURN_DEADLINES.remove(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            RETURNING_THROW_KICK_PROJECTILE_REFS.add(projectileRef);
            THROW_KICK_RETURN_DEADLINES_BY_REF.remove(projectileRef);
        }
    }

    private static boolean isReturningThrowKickProjectile(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            return RETURNING_THROW_KICK_PROJECTILE_UUIDS.contains(projectileUuid);
        }
        return projectileRef != null && RETURNING_THROW_KICK_PROJECTILE_REFS.contains(projectileRef);
    }

    private static void markThrowKickStuck(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            STUCK_THROW_KICK_PROJECTILE_UUIDS.add(projectileUuid);
            RETURNING_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            STUCK_THROW_KICK_PROJECTILE_REFS.add(projectileRef);
            RETURNING_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
        }
    }

    private static void clearThrowKickStuck(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            STUCK_THROW_KICK_PROJECTILE_UUIDS.remove(projectileUuid);
            return;
        }
        if (projectileRef != null) {
            STUCK_THROW_KICK_PROJECTILE_REFS.remove(projectileRef);
        }
    }

    private static boolean isThrowKickStuck(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        UUID projectileUuid = getEntityUuid(store, projectileRef);
        if (projectileUuid != null) {
            return STUCK_THROW_KICK_PROJECTILE_UUIDS.contains(projectileUuid);
        }
        return projectileRef != null && STUCK_THROW_KICK_PROJECTILE_REFS.contains(projectileRef);
    }

    private static boolean syncKnownThrowKickProjectiles(Store<EntityStore> store,
                                                         OwnerHomingState state,
                                                         long nowMs) {
        if (store == null || state == null) {
            return false;
        }

        final boolean[] found = {false};
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> projectileRef = chunk.getReferenceTo(index);
                if (projectileRef == null || !projectileRef.isValid() || !isKnownThrowKickProjectile(store, projectileRef)) {
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
                if (!isShieldCapProjectile(commandBuffer, projectileRef)) {
                    continue;
                }

                found[0] = true;
                TrackedProjectileState trackedState =
                        state.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(nowMs));
                armThrowKickReturnTimeout(store, projectileRef, nowMs);
                trackedState.markAsThrowKick(nowMs);
                trackedState.throwKickStuckInBlock = isThrowKickStuck(store, projectileRef);
                debug("throwKick synced from world | owner=" + state.ownerUuid
                        + " | projectile=" + getEntityUuid(store, projectileRef)
                        + " | returning=" + isReturningThrowKickProjectile(store, projectileRef)
                        + " | stuck=" + trackedState.throwKickStuckInBlock
                        + " | deadline=" + getThrowKickReturnDeadline(store, projectileRef, -1L));
                if (isReturningThrowKickProjectile(store, projectileRef) && !trackedState.isReturningToOwner()) {
                    clearProjectilePhaseHitTargets(store, projectileRef);
                    trackedState.startReturn(ReturnMode.THROW_KICK, nowMs);
                    debug("throwKick synced into returning state | owner=" + state.ownerUuid
                            + " | projectile=" + getEntityUuid(store, projectileRef));
                }
            }
        };
        store.forEachChunk(ProjectileModule.get().getStandardPhysicsProviderComponentType(), chunkConsumer);
        return found[0];
    }

    private static void syncKnownShieldCapProjectiles(Store<EntityStore> store,
                                                      OwnerHomingState state,
                                                      Ref<EntityStore> ownerRef,
                                                      long nowMs) {
        if (store == null || state == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }
        if (ShieldCapThrownHandResolver.resolveActiveThrownHand(store, ownerRef)
                == ShieldCapThrownHandResolver.ActiveThrownHand.NONE) {
            return;
        }

        List<Ref<EntityStore>> explicitCandidates = new ArrayList<>();
        List<Ref<EntityStore>> ambiguousCandidates = new ArrayList<>();

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> projectileRef = chunk.getReferenceTo(index);
                if (projectileRef == null || !projectileRef.isValid() || projectileRef.equals(ownerRef)) {
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

                if (isKnownThrowKickProjectile(store, projectileRef)) {
                    continue;
                }
                String projectileAssetName = getProjectileAssetName(store, projectileRef);
                if (projectileAssetName != null && projectileAssetName.startsWith(FOREIGN_PROJECTILE_ASSET_PREFIX)) {
                    continue;
                }
                if (hasExplicitShieldCapIdentity(commandBuffer.getStore(), projectileRef)) {
                    explicitCandidates.add(projectileRef);
                    continue;
                }
                if (isRememberedShieldCapProjectileRef(commandBuffer.getStore(), projectileRef)
                        || projectileRef.equals(state.lastKnownShieldProjectileRef)
                        || projectileAssetName == null
                        || projectileAssetName.isBlank()) {
                    ambiguousCandidates.add(projectileRef);
                }
            }
        };
        store.forEachChunk(ProjectileModule.get().getStandardPhysicsProviderComponentType(), chunkConsumer);

        List<Ref<EntityStore>> candidatesToSync = explicitCandidates;
        boolean recentMarkFallback = false;
        boolean lastKnownPositionFallback = false;
        if (candidatesToSync.isEmpty() && tryPromoteLastKnownShieldProjectileRef(store, state, ambiguousCandidates)) {
            candidatesToSync = List.of(state.lastKnownShieldProjectileRef);
        } else if (candidatesToSync.isEmpty() && ambiguousCandidates.size() == 1) {
            candidatesToSync = ambiguousCandidates;
        } else if (candidatesToSync.isEmpty()
                && ambiguousCandidates.size() > 1
                && state.lastKnownShieldProjectilePosition != null
                && nowMs - state.lastKnownShieldProjectileSeenAtMs <= LAST_KNOWN_SHIELD_SYNC_GRACE_MS) {
            Ref<EntityStore> nearestCandidate =
                    selectNearestProjectileToPosition(store, state.lastKnownShieldProjectilePosition, ambiguousCandidates);
            if (nearestCandidate != null) {
                candidatesToSync = List.of(nearestCandidate);
                lastKnownPositionFallback = true;
            }
        } else if (candidatesToSync.isEmpty()
                && ambiguousCandidates.size() > 1
                && nowMs - state.lastShieldProjectileMarkedAtMs <= RECENT_SHIELD_MARK_SYNC_GRACE_MS) {
            Ref<EntityStore> nearestCandidate = selectNearestProjectileToOwner(store, ownerRef, ambiguousCandidates);
            if (nearestCandidate != null) {
                candidatesToSync = List.of(nearestCandidate);
                recentMarkFallback = true;
            }
        }

        for (Ref<EntityStore> projectileRef : candidatesToSync) {
            TrackedProjectileState trackedState =
                    state.trackedProjectiles.computeIfAbsent(projectileRef, ignored -> new TrackedProjectileState(nowMs));
            rememberShieldCapProjectile(store, projectileRef);
            String projectileAssetName = getProjectileAssetName(store, projectileRef);
            debug("shield projectile synced from world | owner=" + state.ownerUuid
                    + " | projectile=" + getEntityUuid(store, projectileRef)
                    + " | assetName=" + projectileAssetName
                    + " | returning=" + trackedState.isReturningToOwner()
                    + " | ambiguousFallback=" + ambiguousCandidates.contains(projectileRef)
                    + " | recentMarkFallback=" + recentMarkFallback
                    + " | lastKnownPositionFallback=" + lastKnownPositionFallback);
        }
    }

    private static Ref<EntityStore> selectNearestProjectileToPosition(Store<EntityStore> store,
                                                                      Vector3d targetPosition,
                                                                      List<Ref<EntityStore>> projectileRefs) {
        if (store == null || targetPosition == null || projectileRefs == null || projectileRefs.isEmpty()) {
            return null;
        }

        Ref<EntityStore> bestRef = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Ref<EntityStore> projectileRef : projectileRefs) {
            if (projectileRef == null || !projectileRef.isValid()) {
                continue;
            }

            TransformComponent projectileTransform =
                    store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
            if (projectileTransform == null || projectileTransform.getPosition() == null) {
                continue;
            }

            double distanceSq = targetPosition.distanceSquaredTo(projectileTransform.getPosition());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestRef = projectileRef;
            }
        }

        return bestRef;
    }

    private static boolean tryPromoteLastKnownShieldProjectileRef(Store<EntityStore> store,
                                                                  OwnerHomingState state,
                                                                  List<Ref<EntityStore>> ambiguousCandidates) {
        if (store == null || state == null || ambiguousCandidates == null || ambiguousCandidates.isEmpty()) {
            return false;
        }

        Ref<EntityStore> lastKnownRef = state.lastKnownShieldProjectileRef;
        if (lastKnownRef == null || !lastKnownRef.isValid() || lastKnownRef.getStore() != store) {
            return false;
        }

        for (Ref<EntityStore> projectileRef : ambiguousCandidates) {
            if (projectileRef == lastKnownRef || lastKnownRef.equals(projectileRef)) {
                return true;
            }
        }

        return false;
    }

    private static void restoreLastKnownShieldProjectileRef(Store<EntityStore> store,
                                                            OwnerHomingState state,
                                                            long nowMs) {
        if (store == null || state == null) {
            return;
        }

        Ref<EntityStore> lastKnownRef = state.lastKnownShieldProjectileRef;
        if (lastKnownRef == null || !lastKnownRef.isValid() || lastKnownRef.getStore() != store) {
            return;
        }
        if (state.trackedProjectiles.containsKey(lastKnownRef)) {
            return;
        }
        if (isKnownThrowKickProjectile(store, lastKnownRef)) {
            return;
        }

        StandardPhysicsProvider physicsProvider =
                store.getComponent(lastKnownRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physicsProvider == null) {
            return;
        }
        UUID creatorUuid = physicsProvider.getCreatorUuid();
        if (creatorUuid == null || !creatorUuid.equals(state.ownerUuid)) {
            return;
        }
        if (store.getComponent(lastKnownRef, Player.getComponentType()) != null) {
            return;
        }

        state.trackedProjectiles.put(lastKnownRef, new TrackedProjectileState(nowMs));
        rememberShieldCapProjectile(store, lastKnownRef);
        debug("last known shield ref restored into tracking | owner=" + state.ownerUuid
                + " | projectile=" + getEntityUuid(store, lastKnownRef));
    }

    private static Ref<EntityStore> selectNearestProjectileToOwner(Store<EntityStore> store,
                                                                   Ref<EntityStore> ownerRef,
                                                                   List<Ref<EntityStore>> projectileRefs) {
        if (store == null || ownerRef == null || !ownerRef.isValid() || projectileRefs == null || projectileRefs.isEmpty()) {
            return null;
        }

        TransformComponent ownerTransform = store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        if (ownerTransform == null || ownerTransform.getPosition() == null) {
            return null;
        }

        Ref<EntityStore> bestRef = null;
        double bestDistanceSq = Double.MAX_VALUE;
        Vector3d ownerPos = ownerTransform.getPosition();

        for (Ref<EntityStore> projectileRef : projectileRefs) {
            if (projectileRef == null || !projectileRef.isValid()) {
                continue;
            }

            TransformComponent projectileTransform =
                    store.getComponent(projectileRef, EntityModule.get().getTransformComponentType());
            if (projectileTransform == null || projectileTransform.getPosition() == null) {
                continue;
            }

            double distanceSq = ownerPos.distanceSquaredTo(projectileTransform.getPosition());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestRef = projectileRef;
            }
        }

        return bestRef;
    }

    private static String getProjectileAssetName(Store<EntityStore> store, Ref<EntityStore> projectileRef) {
        if (store == null || projectileRef == null || !projectileRef.isValid()) {
            return null;
        }

        ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
        if (projectileComponent == null) {
            return null;
        }

        String projectileAssetName = projectileComponent.getProjectileAssetName();
        return projectileAssetName == null || projectileAssetName.isBlank() ? null : projectileAssetName;
    }

    private static void clearReturnKickWindow(OwnerHomingState state) {
        if (state == null) {
            return;
        }
        state.lastReturnReticlePulseAtMs = 0L;
        state.returnKickWindowUntilMs = 0L;
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println(LOG_PREFIX + message);
        }
    }

    private static String describeHeldShieldState(Store<EntityStore> store, Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return "owner-invalid";
        }

        Player player = store.getComponent(ownerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return "inventory-missing";
        }

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer utility = inventory.getUtility();
        byte hotbarSlot = inventory.getActiveHotbarSlot();
        byte utilitySlot = inventory.getActiveUtilitySlot();
        String hotbarItem = isInventorySlotUsable(hotbar, hotbarSlot) ? describeItem(hotbar.getItemStack(hotbarSlot)) : "inactive";
        String utilityItem = isInventorySlotUsable(utility, utilitySlot) ? describeItem(utility.getItemStack(utilitySlot)) : "inactive";
        boolean utilityContainsThrown = containsItem(utility, ShieldCapThrownHandResolver.THROWN_ITEM_ID);
        return "hotbar[" + hotbarSlot + "]=" + hotbarItem
                + " | utility[" + utilitySlot + "]=" + utilityItem
                + " | utilityContainsThrown=" + utilityContainsThrown;
    }

    private static String describeItem(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getItemId() + "@dur=" + stack.getDurability();
    }

    private static boolean isEmptyItem(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private static boolean matchesItem(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }

        String stackItemId = stack.getItemId();
        if (stackItemId == null || stackItemId.isBlank()) {
            return false;
        }

        return stackItemId.equals(itemId)
                || stackItemId.endsWith("." + itemId)
                || stackItemId.contains(itemId);
    }

    private static boolean containsItem(ItemContainer container, String itemId) {
        if (container == null || itemId == null || itemId.isBlank()) {
            return false;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInventorySlotUsable(ItemContainer container, byte slot) {
        return container != null
                && slot != Inventory.INACTIVE_SLOT_INDEX
                && slot >= 0
                && slot < container.getCapacity();
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
        private long lastShieldProjectileMarkedAtMs;
        private long lastOwnerAutoReturnFallbackAtMs;
        private ShieldCapThrownHandResolver.ActiveThrownHand lastObservedThrownHand = ShieldCapThrownHandResolver.ActiveThrownHand.NONE;
        private long lastObservedThrownHandAtMs;
        private Ref<EntityStore> lastKnownShieldProjectileRef;
        private Vector3d lastKnownShieldProjectilePosition;
        private long lastKnownShieldProjectileSeenAtMs;
        private long lastReturnReticlePulseAtMs;
        private long returnKickWindowUntilMs;
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
        private long throwKickReturnDeadlineAtMs;
        private ReturnMode returnMode = ReturnMode.NONE;
        private boolean throwKickMode;
        private boolean throwKickStuckInBlock;
        private boolean disableAutoReturn;
        private boolean disableTargetSearch;
        private boolean allowOwnerCatchWithoutReturn;
        private boolean persistWhenStationary;
        private Ref<EntityStore> mjolnirClashTargetRef;
        private boolean fallingAfterProjectileClash;
        private boolean groundedAfterProjectileClash;
        private long ownerContactGraceUntilMs;
        private boolean returnCallingAnimationTriggered;
        private long returnCallingCatchReadyAtMs;
        private final Set<UUID> hitTargetUuids = ConcurrentHashMap.newKeySet();
        private final Set<Ref<EntityStore>> hitTargetRefs = ConcurrentHashMap.newKeySet();

        private TrackedProjectileState(long addedAtMs) {
            this.addedAtMs = addedAtMs;
            this.lastActivityAtMs = addedAtMs;
            this.lastThrowKickBlockImpactAtMs = addedAtMs;
            this.throwKickReturnDeadlineAtMs = addedAtMs + FLIGHT_RETURN_TIMEOUT_MS;
        }

        private void recordActivity(long now) {
            this.lastActivityAtMs = now;
        }

        private void startReturn(ReturnMode returnMode, long now) {
            this.returnMode = returnMode;
            this.throwKickStuckInBlock = false;
            this.clearMjolnirProjectileClash();
            this.clearProjectileClashGroundState();
            this.hitTargetUuids.clear();
            this.hitTargetRefs.clear();
            this.lastActivityAtMs = now;
            this.throwKickReturnDeadlineAtMs = Long.MAX_VALUE;
            this.returnCallingAnimationTriggered = false;
            this.returnCallingCatchReadyAtMs = 0L;
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
            boolean wasThrowKick = this.throwKickMode;
            this.throwKickMode = true;
            this.disableAutoReturn = false;
            this.disableTargetSearch = true;
            this.allowOwnerCatchWithoutReturn = true;
            this.persistWhenStationary = true;
            if (!wasThrowKick) {
                this.hitTargetUuids.clear();
                this.hitTargetRefs.clear();
                this.lastThrowKickBlockImpactAtMs = now;
                this.throwKickReturnDeadlineAtMs = now + FLIGHT_RETURN_TIMEOUT_MS;
                this.ownerContactGraceUntilMs = Math.max(this.ownerContactGraceUntilMs, now + THROW_KICK_OWNER_CONTACT_GRACE_MS);
            }
        }

        private void recordThrowKickBlockImpact(long now) {
            this.lastThrowKickBlockImpactAtMs = now;
            this.throwKickReturnDeadlineAtMs = now + FLIGHT_RETURN_TIMEOUT_MS;
            this.lastActivityAtMs = now;
        }

        private void beginMjolnirProjectileClash(Ref<EntityStore> mjolnirProjectileRef) {
            this.mjolnirClashTargetRef = mjolnirProjectileRef;
        }

        private void clearMjolnirProjectileClash() {
            this.mjolnirClashTargetRef = null;
        }

        private void beginProjectileClashGroundDrop(long now) {
            this.clearMjolnirProjectileClash();
            this.returnMode = ReturnMode.NONE;
            this.disableAutoReturn = true;
            this.disableTargetSearch = true;
            this.allowOwnerCatchWithoutReturn = false;
            this.persistWhenStationary = true;
            this.fallingAfterProjectileClash = true;
            this.groundedAfterProjectileClash = false;
            this.throwKickStuckInBlock = false;
            this.returnCallingAnimationTriggered = false;
            this.returnCallingCatchReadyAtMs = 0L;
            this.lastActivityAtMs = now;
        }

        private void clearProjectileClashGroundState() {
            this.fallingAfterProjectileClash = false;
            this.groundedAfterProjectileClash = false;
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

    private static final class TrackedProjectileHandle {
        private final OwnerHomingState ownerState;
        private final TrackedProjectileState trackedState;

        private TrackedProjectileHandle(OwnerHomingState ownerState,
                                        TrackedProjectileState trackedState) {
            this.ownerState = ownerState;
            this.trackedState = trackedState;
        }
    }
}
