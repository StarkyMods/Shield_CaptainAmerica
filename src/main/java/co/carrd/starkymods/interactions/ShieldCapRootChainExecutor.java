package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

final class ShieldCapRootChainExecutor {
    private ShieldCapRootChainExecutor() {
    }

    static void tryExecuteRoot(@Nonnull InteractionContext context, @Nonnull String rootId) {
        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(rootId);
        if (rootInteraction == null) {
            return;
        }
        context.execute(rootInteraction);
    }
}
