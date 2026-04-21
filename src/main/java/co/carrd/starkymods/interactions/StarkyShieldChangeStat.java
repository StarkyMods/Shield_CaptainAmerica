package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatBaseInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionTarget;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

public final class StarkyShieldChangeStat extends ChangeStatBaseInteraction {
    private static final float DEFAULT_MAX_STAMINA = 10.0f;
    private static final float PERCENT_SCALE = 100.0f;

    @Nonnull
    public static final BuilderCodec<StarkyShieldChangeStat> CODEC =
            BuilderCodec
                    .builder(
                            StarkyShieldChangeStat.class,
                            StarkyShieldChangeStat::new,
                            ChangeStatBaseInteraction.CODEC
                    )
                    .documentation("Works like ChangeStat, but Stamina uses the default 10-point scale for absolute values and respects percent-based changes when ValueType is Percent.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (entityStats == null || entityStats.isEmpty()) {
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> targetRef = resolveTargetRef(context);
        if (commandBuffer == null || targetRef == null || !targetRef.isValid()) {
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        Int2FloatOpenHashMap regularChanges = new Int2FloatOpenHashMap();
        int staminaStatId = DefaultEntityStatTypes.getStamina();
        for (Int2FloatMap.Entry entry : entityStats.int2FloatEntrySet()) {
            int statId = entry.getIntKey();
            float rawValue = entry.getFloatValue();
            if (statId == staminaStatId) {
                applyScaledStaminaChange(statMap, staminaStatId, rawValue);
                continue;
            }

            regularChanges.put(statId, rawValue);
        }

        if (!regularChanges.isEmpty()) {
            statMap.processStatChanges(
                    EntityStatMap.Predictable.ALL,
                    regularChanges,
                    valueType == null ? ValueType.Absolute : valueType,
                    changeStatBehaviour == null ? ChangeStatBehaviour.Add : changeStatBehaviour
            );
        }
    }

    private Ref<EntityStore> resolveTargetRef(@Nonnull InteractionContext context) {
        InteractionTarget target = entityTarget == null ? InteractionTarget.USER : entityTarget;
        return target.getEntity(context, context.getEntity());
    }

    private void applyScaledStaminaChange(@Nonnull EntityStatMap statMap, int staminaStatId, float rawValue) {
        EntityStatValue stamina = statMap.get(staminaStatId);
        if (stamina == null) {
            return;
        }

        float maxValue = stamina.getMax();
        if (maxValue <= 0.0f) {
            return;
        }

        float scaledValue = valueType == ValueType.Percent
                ? maxValue * (rawValue / PERCENT_SCALE)
                : maxValue * (rawValue / DEFAULT_MAX_STAMINA);
        ChangeStatBehaviour behaviour = changeStatBehaviour == null ? ChangeStatBehaviour.Add : changeStatBehaviour;

        if (behaviour == ChangeStatBehaviour.Set) {
            statMap.setStatValue(staminaStatId, scaledValue);
            return;
        }

        if (scaledValue >= 0.0f) {
            statMap.addStatValue(staminaStatId, scaledValue);
        } else {
            statMap.subtractStatValue(staminaStatId, Math.abs(scaledValue));
        }
    }
}
