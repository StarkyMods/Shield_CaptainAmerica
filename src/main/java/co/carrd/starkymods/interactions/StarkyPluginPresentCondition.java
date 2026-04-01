package co.carrd.starkymods.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.plugin.PluginManager;

public class StarkyPluginPresentCondition extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<StarkyPluginPresentCondition> CODEC =
            BuilderCodec
                    .builder(
                            StarkyPluginPresentCondition.class,
                            StarkyPluginPresentCondition::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("PluginAuthor", Codec.STRING),
                            (interaction, value) -> interaction.pluginAuthor = value,
                            interaction -> interaction.pluginAuthor,
                            (interaction, parent) -> interaction.pluginAuthor = parent.pluginAuthor
                    )
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("PluginName", Codec.STRING),
                            (interaction, value) -> interaction.pluginName = value,
                            interaction -> interaction.pluginName,
                            (interaction, parent) -> interaction.pluginName = parent.pluginName
                    )
                    .add()
                    .documentation("Succeeds if the configured plugin is present. Defaults to narwhals/Zephyr.")
                    .build();

    private String pluginAuthor = "narwhals";
    private String pluginName = "Zephyr";

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        if (pluginAuthor == null || pluginAuthor.isBlank() || pluginName == null || pluginName.isBlank()) {
            markFailed(context);
            return;
        }

        boolean present = PluginManager.get().getPlugin(new PluginIdentifier(pluginAuthor, pluginName)) != null;
        if (!present) {
            markFailed(context);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type,
                                    @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
        // Presence is decided by the server only.
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
}
