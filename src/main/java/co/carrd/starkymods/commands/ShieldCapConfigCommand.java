package co.carrd.starkymods.commands;

import co.carrd.starkymods.ui.ShieldCapConfigPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;

public class ShieldCapConfigCommand extends AbstractPlayerCommand {
    private final boolean editable;
    private final boolean editorForOp;

    public ShieldCapConfigCommand(String commandName, boolean editable) {
        super(commandName, editable ? "Open the SHIELDCAP config editor." : "Open the SHIELDCAP config viewer.", false);
        this.editable = editable;
        this.editorForOp = "capshield".equalsIgnoreCase(commandName);
        this.requirePermission("starkymods.shieldcap.command." + commandName.toLowerCase(Locale.ROOT));
        if (!editable) {
            this.setPermissionGroups("hytale:Adventurer");
        }
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean isOp = PermissionsModule.get().hasPermission(playerRef.getUuid(), "*");
        boolean openEditor = editable || (editorForOp && isOp);
        if (editable && !isOp) {
            context.sendMessage(Message.raw("You must be OP to use this command."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            ShieldCapConfigPage page = openEditor
                    ? ShieldCapConfigPage.createSettingsPage(playerRef)
                    : ShieldCapConfigPage.createViewerPage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }
}
