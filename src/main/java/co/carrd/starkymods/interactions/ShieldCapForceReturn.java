package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class ShieldCapForceReturn extends SimpleInstantInteraction {
    private static final boolean DEBUG = false;
    private static final String LOG_PREFIX = "[ShieldCapForceReturnDebug] ";

    @Nonnull
    public static final BuilderCodec<ShieldCapForceReturn> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapForceReturn.class,
                            ShieldCapForceReturn::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Forces the currently active thrown shield projectile to return to its owner.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Player player = resolvePlayer(context.getCommandBuffer(), context);
        if (player == null) {
            return;
        }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        boolean foundProjectile =
                ShieldCapThrowHomingService.forceReturnToOwner(playerRef.getUuid(), playerRef.getReference());
        debug("force return interaction | owner=" + playerRef.getUuid()
                + " | foundProjectile=" + foundProjectile);
        if (!foundProjectile) {
            ShieldCapCatch.restoreToOwner(context.getCommandBuffer().getStore(), playerRef.getReference());
        }
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

    private void debug(String message) {
        if (DEBUG) {
            System.out.println(LOG_PREFIX + message);
        }
    }
}
