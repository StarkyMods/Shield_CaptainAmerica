package co.carrd.starkymods.interactions;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ShieldCapRefreshVisualsInteraction extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapRefreshVisualsInteraction> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapRefreshVisualsInteraction.class,
                            ShieldCapRefreshVisualsInteraction::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin == null) {
            return;
        }

        Player player = resolvePlayer(context.getCommandBuffer(), context);
        if (player == null) {
            return;
        }

        plugin.getVisualSyncService().syncDeferred(player);
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
