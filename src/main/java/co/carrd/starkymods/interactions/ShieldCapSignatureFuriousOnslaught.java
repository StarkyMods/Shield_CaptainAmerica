package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.VelocityThresholdStyle;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapSignatureFuriousOnslaught extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ShieldCapSignatureFuriousOnslaught> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapSignatureFuriousOnslaught.class,
                            ShieldCapSignatureFuriousOnslaught::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("DurationSeconds", Codec.FLOAT),
                            (o, i) -> o.durationSeconds = i,
                            o -> o.durationSeconds,
                            (o, p) -> o.durationSeconds = p.durationSeconds
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("SprintSpeed", Codec.DOUBLE),
                            (o, i) -> o.sprintSpeed = i,
                            o -> o.sprintSpeed,
                            (o, p) -> o.sprintSpeed = p.sprintSpeed
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("UseVelocityConfig", Codec.BOOLEAN),
                            (o, i) -> o.useVelocityConfig = i,
                            o -> o.useVelocityConfig,
                            (o, p) -> o.useVelocityConfig = p.useVelocityConfig
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("GroundSpeedMultiplier", Codec.DOUBLE),
                            (o, i) -> o.groundSpeedMultiplier = i,
                            o -> o.groundSpeedMultiplier,
                            (o, p) -> o.groundSpeedMultiplier = p.groundSpeedMultiplier
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("AirSpeedMultiplier", Codec.DOUBLE),
                            (o, i) -> o.airSpeedMultiplier = i,
                            o -> o.airSpeedMultiplier,
                            (o, p) -> o.airSpeedMultiplier = p.airSpeedMultiplier
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("GroundResistance", Codec.FLOAT),
                            (o, i) -> o.groundResistance = i,
                            o -> o.groundResistance,
                            (o, p) -> o.groundResistance = p.groundResistance
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("GroundResistanceMax", Codec.FLOAT),
                            (o, i) -> o.groundResistanceMax = i,
                            o -> o.groundResistanceMax,
                            (o, p) -> o.groundResistanceMax = p.groundResistanceMax
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("AirResistance", Codec.FLOAT),
                            (o, i) -> o.airResistance = i,
                            o -> o.airResistance,
                            (o, p) -> o.airResistance = p.airResistance
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("AirResistanceMax", Codec.FLOAT),
                            (o, i) -> o.airResistanceMax = i,
                            o -> o.airResistanceMax,
                            (o, p) -> o.airResistanceMax = p.airResistanceMax
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("Threshold", Codec.FLOAT),
                            (o, i) -> o.threshold = i,
                            o -> o.threshold,
                            (o, p) -> o.threshold = p.threshold
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("ResistanceStyle", new EnumCodec<>(VelocityThresholdStyle.class)),
                            (o, i) -> o.resistanceStyle = i,
                            o -> o.resistanceStyle,
                            (o, p) -> o.resistanceStyle = p.resistanceStyle
                    )
                    .add()
                    .documentation("Arms Furious Onslaught: forced forward sprint with collision damage and heavy knockback.")
                    .build();

    protected float durationSeconds = 7.0f;
    protected double sprintSpeed = 6.0d;
    protected boolean useVelocityConfig = false;
    protected double groundSpeedMultiplier = 1.0d;
    protected double airSpeedMultiplier = 0.55d;
    protected float groundResistance = 0.936f;
    protected float groundResistanceMax = 0.928f;
    protected float airResistance = 0.924f;
    protected float airResistanceMax = 0.932f;
    protected float threshold = 3.0f;
    protected VelocityThresholdStyle resistanceStyle = VelocityThresholdStyle.Linear;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (context.getCommandBuffer() == null) {
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            entityRef = context.getOwningEntity();
        }
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        UUIDComponent uuidComponent =
                context.getCommandBuffer().getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null || uuidComponent.getUuid() == null) {
            return;
        }

        ShieldCapSignatureFuriousOnslaughtService.arm(
                uuidComponent.getUuid(),
                entityRef,
                durationSeconds,
                sprintSpeed,
                useVelocityConfig,
                groundSpeedMultiplier,
                airSpeedMultiplier,
                groundResistance,
                groundResistanceMax,
                airResistance,
                airResistanceMax,
                threshold,
                resistanceStyle
        );
    }
}
