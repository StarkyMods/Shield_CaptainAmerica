package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class StarkyMainHandCondition extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<StarkyMainHandCondition> CODEC =
            BuilderCodec
                    .builder(
                            StarkyMainHandCondition.class,
                            StarkyMainHandCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("Item in Main Hand", Codec.STRING),
                            (interaction, value) -> interaction.itemId = value,
                            interaction -> interaction.itemId,
                            (interaction, parent) -> interaction.itemId = parent.itemId
                    )
                    .add()
                    .documentation("Succeeds only if the player's main-hand item matches the configured item id.")
                    .build();

    private String itemId;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Player player = resolvePlayer(context.getCommandBuffer(), context);
        if (player == null || itemId == null || itemId.isBlank()) {
            markFailed(context);
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            markFailed(context);
            return;
        }

        ItemStack mainHandStack = inventory.getItemInHand();
        if (!matchesConfiguredId(mainHandStack)) {
            markFailed(context);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type,
                                    @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // This condition depends on server inventory state.
    }

    private boolean matchesConfiguredId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String stackItemId = stack.getItemId();
        if (stackItemId == null || stackItemId.isBlank()) {
            return false;
        }

        return stackItemId.equals(itemId)
                || stackItemId.endsWith("." + itemId)
                || stackItemId.contains(itemId);
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
