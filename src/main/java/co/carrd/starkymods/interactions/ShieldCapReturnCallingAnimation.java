package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class ShieldCapReturnCallingAnimation extends SimpleInstantInteraction {
    private static final String RIGHT_ROOT_ID = "Root_ShieldCap_Return_Calling_Right_Internal";
    private static final String LEFT_ROOT_ID = "Root_ShieldCap_Return_Calling_Left_Internal";

    @Nonnull
    public static final BuilderCodec<ShieldCapReturnCallingAnimation> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapReturnCallingAnimation.class,
                            ShieldCapReturnCallingAnimation::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .documentation("Plays the appropriate ShieldCap return-calling animation for the active hand.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ownerRef = context.getEntity();
        if (ownerRef == null || !ownerRef.isValid()) {
            ownerRef = context.getOwningEntity();
        }
        if (ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        Store<EntityStore> store = context.getCommandBuffer() == null ? null : context.getCommandBuffer().getStore();
        ShieldCapThrownHandResolver.ActiveThrownHand activeHand =
                ShieldCapThrownHandResolver.resolveActiveThrownHand(store, ownerRef);
        String rootId = activeHand == ShieldCapThrownHandResolver.ActiveThrownHand.LEFT ? LEFT_ROOT_ID : RIGHT_ROOT_ID;
        ShieldCapRootChainExecutor.tryExecuteRoot(context, rootId);
    }
}
