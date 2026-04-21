package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionBaseInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;

public final class StarkyShieldStatsCondition extends StatsConditionBaseInteraction {
    private static final float DEFAULT_MAX_STAMINA = 10.0f;
    private static final float PERCENT_SCALE = 100.0f;

    @Nonnull
    public static final BuilderCodec<StarkyShieldStatsCondition> CODEC =
            BuilderCodec
                    .builder(
                            StarkyShieldStatsCondition.class,
                            StarkyShieldStatsCondition::new,
                            StatsConditionBaseInteraction.CODEC
                    )
                    .documentation("Works like StatsCondition, but Stamina uses the default 10-point scale for absolute costs, and percent checks use the player's real current max.")
                    .build();

    @Override
    protected boolean canAfford(@Nonnull Ref<EntityStore> entityRef,
                                @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        if (entityRef == null || !entityRef.isValid() || costs == null || costs.isEmpty()) {
            return false;
        }

        EntityStatMap statMap = componentAccessor.getComponent(entityRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return false;
        }

        int staminaStatId = DefaultEntityStatTypes.getStamina();
        int signatureEnergyStatId = DefaultEntityStatTypes.getSignatureEnergy();
        for (Int2FloatMap.Entry entry : costs.int2FloatEntrySet()) {
            int statId = entry.getIntKey();
            float configuredCost = entry.getFloatValue();
            EntityStatValue statValue = statMap.get(statId);
            if (statValue == null) {
                return false;
            }

            float currentValue;
            float requiredValue;
            if (statId == staminaStatId) {
                float maxValue = statValue.getMax();
                if (maxValue <= 0.0f) {
                    return false;
                }
                if (valueType == ValueType.Percent) {
                    currentValue = (statValue.get() / maxValue) * PERCENT_SCALE;
                    requiredValue = configuredCost;
                } else {
                    currentValue = (statValue.get() / maxValue) * DEFAULT_MAX_STAMINA;
                    requiredValue = configuredCost;
                }
            } else if (statId == signatureEnergyStatId && valueType == ValueType.Percent) {
                float maxValue = statValue.getMax();
                if (maxValue <= 0.0f) {
                    return false;
                }
                currentValue = (statValue.get() / maxValue) * PERCENT_SCALE;
                requiredValue = configuredCost;
            } else {
                currentValue = valueType == ValueType.Percent ? statValue.asPercentage() : statValue.get();
                requiredValue = configuredCost;
            }

            if (lessThan) {
                if (!(currentValue < requiredValue)) {
                    return false;
                }
                continue;
            }

            if (currentValue >= requiredValue) {
                continue;
            }

            if (!canOverdraw(currentValue, requiredValue)) {
                return false;
            }
        }

        return true;
    }
}
