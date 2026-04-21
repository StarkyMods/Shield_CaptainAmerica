package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapThrowKickRootRouter extends SimpleInstantInteraction {
    private static final String MJOLNIR_WEAPON_ID = "Weapon_Mjolnir_Starky";
    private static final String SHIELDCAP_THROW_KICK_INTERNAL_ROOT_ID = "Root_ShieldCap_Throw_Kick_Internal";
    private static final String MJOLNIR_SHIELD_THROW_KICK_ROOT_ID = "Root_Mjolnir_Shield_Throw_Kick";

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowKickRootRouter> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowKickRootRouter.class,
                            ShieldCapThrowKickRootRouter::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Routes ShieldCap throw-kick execution during the return window. Uses the Mjolnir-specific shield throw-kick only when Weapon_Mjolnir_Starky is in main hand.")
                    .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        RootInteraction rootInteraction = resolveTargetRootInteraction(context);
        if (rootInteraction == null) {
            markFailed(context);
            return;
        }

        executeTargetRoot(type, context, rootInteraction);
        markFailed(context);
    }

    private static RootInteraction resolveTargetRootInteraction(@Nonnull InteractionContext context) {
        RootInteraction mjolnirRoot = RootInteraction.getAssetMap().getAsset(MJOLNIR_SHIELD_THROW_KICK_ROOT_ID);
        if (isMjolnirInMainHand(context) && mjolnirRoot != null) {
            return mjolnirRoot;
        }

        return RootInteraction.getAssetMap().getAsset(SHIELDCAP_THROW_KICK_INTERNAL_ROOT_ID);
    }

    private static boolean isMjolnirInMainHand(@Nonnull InteractionContext context) {
        if (context.getCommandBuffer() == null) {
            return false;
        }

        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            playerRef = context.getOwningEntity();
        }
        if (playerRef == null || !playerRef.isValid()) {
            return false;
        }

        Player player = context.getCommandBuffer().getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return false;
        }

        Inventory inventory = player.getInventory();
        ItemStack itemInHand = inventory.getItemInHand();
        return itemInHand != null && MJOLNIR_WEAPON_ID.equals(itemInHand.getItemId());
    }

    private static void executeTargetRoot(@Nonnull InteractionType sourceType,
                                          @Nonnull InteractionContext context,
                                          @Nonnull RootInteraction rootInteraction) {
        if (context.getChain() == null) {
            context.execute(rootInteraction);
            return;
        }

        try {
            context.fork(sourceType, context.duplicate(), rootInteraction, false);
        } catch (Throwable ignored) {
            context.execute(rootInteraction);
        }
    }

    private static void markFailed(@Nonnull InteractionContext context) {
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
