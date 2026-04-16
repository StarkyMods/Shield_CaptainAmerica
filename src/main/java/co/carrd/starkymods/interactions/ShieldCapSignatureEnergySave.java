package co.carrd.starkymods.interactions;

import co.carrd.starkymods.config.ShieldCapConfigManager;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ShieldCapSignatureEnergySave extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapSignatureEnergySave> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapSignatureEnergySave.class,
                            ShieldCapSignatureEnergySave::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (!ShieldCapConfigManager.isSignatureEnergyKeepOnSwapEnabled()) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = resolvePlayer(commandBuffer, context);
        if (player == null) {
            return;
        }

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        Object entityStatComponent = commandBuffer.getComponent(playerRef, EntityStatMap.getComponentType());
        if (!(entityStatComponent instanceof EntityStatMap statMap)) {
            return;
        }

        int signatureEnergyId = DefaultEntityStatTypes.getSignatureEnergy();
        EntityStatValue signatureEnergy = statMap.get(signatureEnergyId);
        if (signatureEnergy == null) {
            return;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        ShieldCapSignatureEnergyStore.save(uuid, signatureEnergy.get());
    }

    private Player resolvePlayer(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                 @Nonnull InteractionContext context) {
        Player player = getPlayerFromRef(commandBuffer, context.getEntity());
        if (player != null) {
            return player;
        }

        return getPlayerFromRef(commandBuffer, context.getOwningEntity());
    }

    private Player getPlayerFromRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        if (component instanceof Player player) {
            return player;
        }

        return null;
    }
}
