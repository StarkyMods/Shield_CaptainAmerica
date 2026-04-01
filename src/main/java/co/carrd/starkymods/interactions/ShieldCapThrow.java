package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;

public final class ShieldCapThrow extends SimpleInstantInteraction {
    private static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";

    @Nonnull
    public static final BuilderCodec<ShieldCapThrow> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrow.class,
                            ShieldCapThrow::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        swapCurrentMainHandItem(context, THROWN_ITEM_ID);
    }

    private void swapCurrentMainHandItem(@Nonnull InteractionContext context, @Nonnull String targetItemId) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Object component = commandBuffer.getComponent(ref, Player.getComponentType());
        if (!(component instanceof Player player)) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        ItemContainer hotbarContainer = inventory.getHotbar();
        if (hotbarContainer == null) {
            return;
        }

        ItemStack itemInHand = context.getHeldItem();
        if (itemInHand == null || itemInHand.isEmpty()) {
            itemInHand = inventory.getItemInHand();
        }
        if (itemInHand == null || itemInHand.isEmpty()) {
            return;
        }

        String rawMetadata = itemInHand.toPacket().metadata;
        BsonDocument copiedMetadata = null;
        if (rawMetadata != null && !rawMetadata.isBlank()) {
            try {
                copiedMetadata = BsonDocument.parse(rawMetadata);
            } catch (Exception ignored) {
                copiedMetadata = null;
            }
        }

        byte activeSlot = inventory.getActiveHotbarSlot();
        ItemStack newItem = new ItemStack(targetItemId, 1, copiedMetadata);

        double copiedDurability = itemInHand.getDurability();
        if (Double.isFinite(copiedDurability) && copiedDurability >= 0.0) {
            newItem = newItem.withDurability(copiedDurability);
        }

        hotbarContainer.setItemStackForSlot(activeSlot, newItem);
    }
}
