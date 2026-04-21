package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class StarkyMjolnirDownstrikeBridge extends SimpleInstantInteraction {
    private static final String DOWNSTRIKE_CONFIG_TARGET_ID = "Mjolnir_Downstrike_OSC";
    private static final String CONTROL_ROOT_ID = "Root_Mjolnir_Control_Downstrike_OSC";
    private static final float DEFAULT_MAX_STAMINA = 10.0f;
    private static final float NORMAL_STAMINA_COST = 4.5f;
    private static final float NORMAL_MANA_COST = 80.0f;
    private static final float OVERCHARGED_STAMINA_COST = 3.2f;
    private static final float OVERCHARGED_MANA_COST = 45.0f;
    @Nonnull
    public static final BuilderCodec<StarkyMjolnirDownstrikeBridge> CODEC =
            BuilderCodec
                    .builder(
                            StarkyMjolnirDownstrikeBridge.class,
                            StarkyMjolnirDownstrikeBridge::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Executes Mjolnir downstrike from ShieldCap when the player is airborne from a jump.")
                    .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (!isDownstrikeEnabledInMjolnirConfig()) {
            markFailed(context);
            return;
        }

        if (!isAirborneFromJump(context)) {
            markFailed(context);
            return;
        }

        if (!canAffordDownstrike(context)) {
            markFailed(context);
            return;
        }

        if (executeDownstrike(context)) {
            return;
        }
        markFailed(context);
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type,
                                    @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // Depends on server-side movement state and loaded cross-plugin assets.
    }

    private boolean isDownstrikeEnabledInMjolnirConfig() {
        try {
            Class<?> configManagerClass = Class.forName("co.carrd.starkymods.config.ConfigManager");
            Object enabled = configManagerClass
                    .getMethod("isConfigCheckedInteractionEnabled", String.class)
                    .invoke(null, DOWNSTRIKE_CONFIG_TARGET_ID);
            return !(enabled instanceof Boolean) || (Boolean) enabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isAirborneFromJump(@Nonnull InteractionContext context) {
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
        return movementStates != null
                && !movementStates.onGround
                && ShieldCapPrimaryJumpHitCooldown.isJumpMarked(context);
    }

    private boolean canAffordDownstrike(@Nonnull InteractionContext context) {
        EntityStatMap statMap = resolvePlayerStatMap(context);
        if (statMap == null) {
            return false;
        }

        boolean overchargedVariant = isOverchargedVariant(statMap);
        float requiredStamina = overchargedVariant ? OVERCHARGED_STAMINA_COST : NORMAL_STAMINA_COST;
        float requiredMana = overchargedVariant ? OVERCHARGED_MANA_COST : NORMAL_MANA_COST;
        return getScaledStamina(statMap) >= requiredStamina && getRawStatValue(statMap, "Mana") >= requiredMana;
    }

    private boolean executeDownstrike(@Nonnull InteractionContext context) {
        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(CONTROL_ROOT_ID);
        if (rootInteraction == null) {
            return false;
        }

        try {
            context.execute(rootInteraction);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isOverchargedVariant(@Nonnull EntityStatMap statMap) {
        int overchargedStatusStatId = EntityStatType.getAssetMap().getIndexOrDefault("MjolnirOverchargedStatus", -1);
        if (overchargedStatusStatId == -1) {
            return false;
        }

        EntityStatValue statValue = statMap.get(overchargedStatusStatId);
        return statValue != null && statValue.get() < 1.0f;
    }

    private float getScaledStamina(@Nonnull EntityStatMap statMap) {
        EntityStatValue staminaValue = statMap.get(DefaultEntityStatTypes.getStamina());
        if (staminaValue == null || staminaValue.getMax() <= 0.0f) {
            return 0.0f;
        }
        return (staminaValue.get() / staminaValue.getMax()) * DEFAULT_MAX_STAMINA;
    }

    private float getRawStatValue(@Nonnull EntityStatMap statMap, @Nonnull String statId) {
        int resolvedStatId = EntityStatType.getAssetMap().getIndexOrDefault(statId, -1);
        if (resolvedStatId == -1) {
            return 0.0f;
        }

        EntityStatValue statValue = statMap.get(resolvedStatId);
        return statValue == null ? 0.0f : statValue.get();
    }

    private EntityStatMap resolvePlayerStatMap(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return null;
        }

        Ref<EntityStore> entityRef = context.getOwningEntity();
        if (entityRef != null && entityRef.isValid()) {
            EntityStatMap statMap = context.getCommandBuffer().getComponent(entityRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                return statMap;
            }
        }

        entityRef = context.getEntity();
        if (entityRef != null && entityRef.isValid()) {
            return context.getCommandBuffer().getComponent(entityRef, EntityStatMap.getComponentType());
        }

        return null;
    }

    private void markFailed(@Nonnull InteractionContext context) {
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
