package co.carrd.starkymods.interactions;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.VelocityThresholdStyle;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapSignatureFuriousOnslaughtService {
    private static final HytaleLogger LOGGER = StarkyShieldCaptainAmerica.getInstance().getLogger();
    private static final boolean DEBUG = true;
    private static final String LOG_PREFIX = "[ShieldCapOnslaughtDebug] ";
    private static final Map<UUID, OnslaughtState> ACTIVE = new ConcurrentHashMap<>();
    private static final String SELECTOR_ROOT_ID = "Root_ShieldCap_Signature_Furious_Onslaught_Selector_Internal";
    private static final long SELECTOR_PULSE_INTERVAL_MS = 83L;
    private static final InteractionType SELECTOR_LANE = InteractionType.Secondary;
    private static final Field GROUND_RESISTANCE_FIELD = getVelocityConfigField("groundResistance");
    private static final Field GROUND_RESISTANCE_MAX_FIELD = getVelocityConfigField("groundResistanceMax");
    private static final Field AIR_RESISTANCE_FIELD = getVelocityConfigField("airResistance");
    private static final Field AIR_RESISTANCE_MAX_FIELD = getVelocityConfigField("airResistanceMax");
    private static final Field THRESHOLD_FIELD = getVelocityConfigField("threshold");
    private static final Field STYLE_FIELD = getVelocityConfigField("style");

    private ShieldCapSignatureFuriousOnslaughtService() {
    }

    public static void arm(UUID ownerUuid,
                           Ref<EntityStore> ownerRef,
                           float durationSeconds,
                           double sprintSpeed,
                           boolean useVelocityConfig,
                           double groundSpeedMultiplier,
                           double airSpeedMultiplier,
                           float groundResistance,
                           float groundResistanceMax,
                           float airResistance,
                           float airResistanceMax,
                           float threshold,
                           VelocityThresholdStyle resistanceStyle) {
        if (ownerUuid == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        OnslaughtState state = new OnslaughtState();
        state.ownerRef = ownerRef;
        state.armedStore = ownerRef.getStore();
        state.expiresAtMs = System.currentTimeMillis() + (long) (Math.max(0.1f, durationSeconds) * 1000.0f);
        state.sprintSpeed = Math.max(0.1d, sprintSpeed);
        state.groundSpeedMultiplier = Math.max(0.0d, groundSpeedMultiplier);
        state.airSpeedMultiplier = Math.max(0.0d, airSpeedMultiplier);
        state.nextSelectorPulseAtMs = 0L;
        state.velocityConfig = useVelocityConfig
                ? buildVelocityConfig(
                        groundResistance,
                        groundResistanceMax,
                        airResistance,
                        airResistanceMax,
                        threshold,
                        resistanceStyle == null ? VelocityThresholdStyle.Linear : resistanceStyle
                )
                : null;
        ACTIVE.put(ownerUuid, state);
    }

    public static boolean isActive(UUID ownerUuid) {
        if (ownerUuid == null) {
            return false;
        }
        OnslaughtState state = ACTIVE.get(ownerUuid);
        return state != null && System.currentTimeMillis() < state.expiresAtMs;
    }

    public static void tickStore(Store<EntityStore> store) {
        if (store == null || ACTIVE.isEmpty()) {
            return;
        }

        EntityStore entityStore = store.getExternalData();
        if (entityStore == null) {
            return;
        }

        World world = entityStore.getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        ACTIVE.entrySet().removeIf(entry -> !tickOne(store, world, entry.getKey(), entry.getValue()));
        processSelectorPulses(store);
    }

    private static boolean tickOne(Store<EntityStore> store,
                                   World world,
                                   UUID ownerUuid,
                                   OnslaughtState state) {
        if (state.armedStore != null && state.armedStore != store) {
            return true;
        }

        Ref<EntityStore> ownerRef = state.ownerRef;
        if (ownerRef == null || ownerRef.getStore() != store || !ownerRef.isValid()) {
            ownerRef = world.getEntityRef(ownerUuid);
            if (ownerRef == null || !ownerRef.isValid()) {
                return false;
            }
            state.ownerRef = ownerRef;
            state.armedStore = store;
        }

        if (System.currentTimeMillis() >= state.expiresAtMs) {
            return false;
        }

        Player ownerPlayer = store.getComponent(ownerRef, Player.getComponentType());
        TransformComponent ownerTransform =
                store.getComponent(ownerRef, EntityModule.get().getTransformComponentType());
        MovementStatesComponent movementStatesComponent =
                store.getComponent(ownerRef, EntityModule.get().getMovementStatesComponentType());
        Velocity ownerVelocity =
                store.getComponent(ownerRef, EntityModule.get().getVelocityComponentType());
        if (ownerPlayer == null || ownerTransform == null || ownerVelocity == null) {
            return false;
        }

        forceSprintForward(ownerTransform, movementStatesComponent, ownerVelocity, state);
        return true;
    }

    private static void processSelectorPulses(Store<EntityStore> store) {
        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(SELECTOR_ROOT_ID);
        if (rootInteraction == null) {
            debug("missing root " + SELECTOR_ROOT_ID);
            return;
        }

        long now = System.currentTimeMillis();
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

                OnslaughtState state = ACTIVE.get(playerRef.getUuid());
                if (state == null || now >= state.expiresAtMs) {
                    continue;
                }
                if (state.nextSelectorPulseAtMs > now) {
                    continue;
                }

                InteractionManager interactionManager =
                        commandBuffer.getComponent(playerRefEntity, InteractionModule.get().getInteractionManagerComponent());
                if (interactionManager == null) {
                    debug("no interactionManager for owner=" + playerRef.getUuid());
                    continue;
                }

                boolean canRun = interactionManager.canRun(SELECTOR_LANE, rootInteraction);
                boolean started = false;
                String error = null;
                try {
                    InteractionContext context =
                            InteractionContext.forInteraction(interactionManager, playerRefEntity, SELECTOR_LANE, commandBuffer);
                    interactionManager.startChain(
                            playerRefEntity,
                            commandBuffer,
                            SELECTOR_LANE,
                            context,
                            rootInteraction
                    );
                    started = true;
                } catch (Throwable t) {
                    error = t.getClass().getSimpleName() + ": " + t.getMessage();
                }

                debug("pulse owner=" + playerRef.getUuid()
                        + " root=" + SELECTOR_ROOT_ID
                        + " lane=" + SELECTOR_LANE.name()
                        + " canRun=" + canRun
                        + " started=" + started
                        + (error == null ? "" : " error=" + error)
                        + " now=" + now);
                state.nextSelectorPulseAtMs = now + SELECTOR_PULSE_INTERVAL_MS;
            }
        };
        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }

    private static void forceSprintForward(TransformComponent transformComponent,
                                           MovementStatesComponent movementStatesComponent,
                                           Velocity velocity,
                                           OnslaughtState state) {
        Vector3d currentVelocity = velocity.getVelocity();
        if (currentVelocity == null) {
            return;
        }

        Transform transform = transformComponent.getTransform();
        Vector3d direction = transform == null ? null : transform.getDirection();
        double directionX = direction != null ? direction.x : 0.0d;
        double directionZ = direction != null ? direction.z : 1.0d;
        double horizontalLength = Math.sqrt((directionX * directionX) + (directionZ * directionZ));

        if (horizontalLength < 1.0e-6d) {
            directionX = currentVelocity.x;
            directionZ = currentVelocity.z;
            horizontalLength = Math.sqrt((directionX * directionX) + (directionZ * directionZ));
        }

        if (horizontalLength < 1.0e-6d) {
            directionX = 0.0d;
            directionZ = 1.0d;
            horizontalLength = 1.0d;
        }

        directionX /= horizontalLength;
        directionZ /= horizontalLength;

        if (movementStatesComponent != null) {
            MovementStates movementStates = movementStatesComponent.getMovementStates();
            MovementStates sprintStates = movementStates == null ? new MovementStates() : new MovementStates(movementStates);
            sprintStates.idle = false;
            sprintStates.horizontalIdle = false;
            sprintStates.walking = false;
            sprintStates.running = true;
            sprintStates.sprinting = true;
            movementStatesComponent.setMovementStates(sprintStates);
        }

        boolean onGround = movementStatesComponent != null
                && movementStatesComponent.getMovementStates() != null
                && movementStatesComponent.getMovementStates().onGround;
        double speedMultiplier = onGround ? state.groundSpeedMultiplier : state.airSpeedMultiplier;
        double forcedSpeed = state.sprintSpeed * speedMultiplier;
        double forcedX = directionX * forcedSpeed;
        double forcedZ = directionZ * forcedSpeed;
        double deltaX = forcedX - currentVelocity.x;
        double deltaZ = forcedZ - currentVelocity.z;
        velocity.addInstruction(
                new Vector3d(deltaX, 0.0d, deltaZ),
                state.velocityConfig,
                ChangeVelocityType.Add
        );

        Vector3d clientVelocity = velocity.getClientVelocity();
        double clientY = clientVelocity != null ? clientVelocity.y : currentVelocity.y;
        velocity.setClient(forcedX, clientY, forcedZ);
    }

    private static final class OnslaughtState {
        private Ref<EntityStore> ownerRef;
        private Store<EntityStore> armedStore;
        private long expiresAtMs;
        private double sprintSpeed;
        private double groundSpeedMultiplier;
        private double airSpeedMultiplier;
        private long nextSelectorPulseAtMs;
        private VelocityConfig velocityConfig;
    }

    private static VelocityConfig buildVelocityConfig(float groundResistance,
                                                      float groundResistanceMax,
                                                      float airResistance,
                                                      float airResistanceMax,
                                                      float threshold,
                                                      VelocityThresholdStyle style) {
        VelocityConfig config = new VelocityConfig();
        setField(GROUND_RESISTANCE_FIELD, config, groundResistance);
        setField(GROUND_RESISTANCE_MAX_FIELD, config, groundResistanceMax);
        setField(AIR_RESISTANCE_FIELD, config, airResistance);
        setField(AIR_RESISTANCE_MAX_FIELD, config, airResistanceMax);
        setField(THRESHOLD_FIELD, config, threshold);
        setField(STYLE_FIELD, config, style);
        return config;
    }

    private static Field getVelocityConfigField(String name) {
        try {
            Field field = VelocityConfig.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access VelocityConfig field: " + name, e);
        }
    }

    private static void setField(Field field, VelocityConfig config, Object value) {
        try {
            field.set(config, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set VelocityConfig field: " + field.getName(), e);
        }
    }

    private static void debug(String message) {
        if (!DEBUG || LOGGER == null) {
            return;
        }
        LOGGER.atInfo().log(LOG_PREFIX + message);
    }
}
