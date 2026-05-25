package co.carrd.starkymods.interactions;

import co.carrd.starkymods.config.ShieldCapConfigManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCapGuardFallDamageReductionSystem extends DamageEventSystem {
    private static final Set<String> GUARD_WIELD_INTERACTION_IDS = Set.of(
            "ShieldCap_Secondary_Guard_Wield",
            "ShieldCapLeft_Secondary_Guard_Wield",
            "ShieldCapLeft_Mjolnir_Secondary_Guard_Wield"
    );
    private static final String FALL_DAMAGE_ROLL_ROOT_ID = "Root_ShieldCap_Guard_FallDamage_Roll";
    private static final float REQUIRED_STAMINA_RATIO = 0.20f;
    private static final float FALL_DAMAGE_MULTIPLIER = 0.05f;
    private static final double FALLSTAR_SAFE_FALL_DISTANCE = 8.0d;
    private static final InteractionType FALL_DAMAGE_ROLL_LANE = InteractionType.Ability2;
    private static final Map<UUID, Long> TEMPORARY_PROTECTION_UNTIL_MS = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                Player.getComponentType(),
                EntityStatMap.getComponentType()
        );
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage == null || damage.isCancelled() || !isFallDamage(damage)) {
            return;
        }
        if (!ShieldCapConfigManager.isFallResistanceWhenBlockingEnabled()) {
            return;
        }

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        InteractionManager interactionManager =
                commandBuffer.getComponent(playerRef, InteractionModule.get().getInteractionManagerComponent());
        UUID playerUuid = resolvePlayerUuid(playerRef, commandBuffer);
        boolean guardWieldActive = isGuardWieldActive(interactionManager);
        boolean temporaryProtectionActive = isTemporaryProtectionActive(playerUuid);
        if (!guardWieldActive && !temporaryProtectionActive) {
            return;
        }
        boolean shouldTriggerRoll = guardWieldActive && !temporaryProtectionActive;

        Player player = chunk.getComponent(index, Player.getComponentType());
        double currentFallDistance = player != null ? player.getCurrentFallDistance() : 0.0d;

        if (temporaryProtectionActive && currentFallDistance < FALLSTAR_SAFE_FALL_DISTANCE) {
            damage.setAmount(0.0f);
            return;
        }

        EntityStatMap statMap = chunk.getComponent(index, EntityStatMap.getComponentType());
        if (!hasMoreThanRequiredStamina(statMap)) {
            return;
        }

        float currentAmount = damage.getAmount();
        if (currentAmount <= 0.0f) {
            return;
        }

        damage.setAmount(currentAmount * FALL_DAMAGE_MULTIPLIER);
        consumeRequiredStamina(statMap);
        if (shouldTriggerRoll) {
            triggerFallDamageRoll(playerRef, commandBuffer, interactionManager);
        }
    }

    public static void armTemporaryProtection(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        TEMPORARY_PROTECTION_UNTIL_MS.put(playerUuid, Long.MAX_VALUE);
    }

    public static void scheduleTemporaryProtectionClear(UUID playerUuid, long delayMs) {
        if (playerUuid == null) {
            return;
        }
        long untilMs = System.currentTimeMillis() + Math.max(0L, delayMs);
        TEMPORARY_PROTECTION_UNTIL_MS.put(playerUuid, untilMs);
    }

    private static boolean isFallDamage(Damage damage) {
        DamageCause cause = damage.getCause();
        if (cause == null) {
            return false;
        }

        if (cause == DamageCause.FALL) {
            return true;
        }

        String causeId = cause.getId();
        return causeId != null && causeId.equalsIgnoreCase("Fall");
    }

    static boolean isGuardWieldActive(InteractionManager interactionManager) {
        if (interactionManager == null) {
            return false;
        }

        Boolean guardRunning = interactionManager.forEachInteraction(
                (chain, interaction, acc) -> {
                    if (acc) {
                        return true;
                    }
                    String id = interaction.getId();
                    if (id == null) {
                        return false;
                    }
                    for (String guardWieldInteractionId : GUARD_WIELD_INTERACTION_IDS) {
                        if (guardWieldInteractionId.equals(id) || id.contains(guardWieldInteractionId)) {
                            return true;
                        }
                    }
                    return false;
                },
                Boolean.FALSE
        );

        return Boolean.TRUE.equals(guardRunning);
    }

    static boolean isTemporaryProtectionActive(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        Long untilMs = TEMPORARY_PROTECTION_UNTIL_MS.get(playerUuid);
        if (untilMs == null) {
            return false;
        }

        if (untilMs == Long.MAX_VALUE) {
            return true;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs <= untilMs) {
            return true;
        }

        TEMPORARY_PROTECTION_UNTIL_MS.remove(playerUuid, untilMs);
        return false;
    }

    private static UUID resolvePlayerUuid(Ref<EntityStore> playerRef,
                                          CommandBuffer<EntityStore> commandBuffer) {
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private static boolean hasMoreThanRequiredStamina(EntityStatMap statMap) {
        if (statMap == null) {
            return false;
        }

        EntityStatValue stamina = statMap.get(DefaultEntityStatTypes.getStamina());
        if (stamina == null || stamina.getMax() <= 0.0f) {
            return false;
        }

        return (stamina.get() / stamina.getMax()) > REQUIRED_STAMINA_RATIO;
    }

    private static void consumeRequiredStamina(EntityStatMap statMap) {
        if (statMap == null) {
            return;
        }

        EntityStatValue stamina = statMap.get(DefaultEntityStatTypes.getStamina());
        if (stamina == null || stamina.getMax() <= 0.0f) {
            return;
        }

        float staminaCost = stamina.getMax() * REQUIRED_STAMINA_RATIO;
        if (staminaCost <= 0.0f) {
            return;
        }

        statMap.subtractStatValue(DefaultEntityStatTypes.getStamina(), staminaCost);
    }

    static void triggerFallDamageRoll(Ref<EntityStore> playerRef,
                                      CommandBuffer<EntityStore> commandBuffer,
                                      InteractionManager interactionManager) {
        if (playerRef == null || !playerRef.isValid() || interactionManager == null) {
            return;
        }

        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(FALL_DAMAGE_ROLL_ROOT_ID);
        if (rootInteraction == null) {
            return;
        }

        try {
            InteractionContext context =
                    InteractionContext.forInteraction(interactionManager, playerRef, FALL_DAMAGE_ROLL_LANE, commandBuffer);
            interactionManager.startChain(playerRef, commandBuffer, FALL_DAMAGE_ROLL_LANE, context, rootInteraction);
        } catch (Throwable ignored) {
            // Best-effort visual/animation response only.
        }
    }

}
