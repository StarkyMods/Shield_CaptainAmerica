package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ShieldCapThrowHomingEnable extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingEnable> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingEnable.class,
                            ShieldCapThrowHomingEnable::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        ResolvedPlayer resolved = resolvePlayer(commandBuffer, context);
        if (resolved == null || resolved.player == null) {
            return;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(resolved.playerRef, UUIDComponent.getComponentType());
        UUID ownerUuid = uuidComponent == null ? null : uuidComponent.getUuid();
        if (ownerUuid == null) {
            return;
        }

        ShieldCapThrowHomingService.enableFor(ownerUuid, resolved.playerRef);
    }

    private ResolvedPlayer resolvePlayer(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                         @Nonnull InteractionContext context) {
        Ref<EntityStore> entityRef = context.getEntity();
        Player player = getPlayerFromRef(commandBuffer, entityRef);
        if (player != null) {
            return new ResolvedPlayer(player, entityRef);
        }

        Ref<EntityStore> owningRef = context.getOwningEntity();
        player = getPlayerFromRef(commandBuffer, owningRef);
        if (player != null) {
            return new ResolvedPlayer(player, owningRef);
        }

        return null;
    }

    private Player getPlayerFromRef(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        return component instanceof Player player ? player : null;
    }

    private static final class ResolvedPlayer {
        private final Player player;
        private final Ref<EntityStore> playerRef;

        private ResolvedPlayer(Player player, Ref<EntityStore> playerRef) {
            this.player = player;
            this.playerRef = playerRef;
        }
    }
}
