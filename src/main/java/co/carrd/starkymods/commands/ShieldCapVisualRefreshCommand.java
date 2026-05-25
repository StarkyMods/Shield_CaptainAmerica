package co.carrd.starkymods.commands;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import co.carrd.starkymods.visuals.ShieldCapVisualSyncService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class ShieldCapVisualRefreshCommand extends AbstractPlayerCommand {
    public ShieldCapVisualRefreshCommand() {
        super("capshieldrefresh", "Refresh Cap's Shield back visual.", false);
        this.requirePermission("starkymods.shieldcap.command.capshieldrefresh");
        this.setPermissionGroups("hytale:Adventurer");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin == null) {
            context.sendMessage(Message.raw("ShieldCap is not ready."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        plugin.getVisualSyncService().syncDeferred(
                player,
                true,
                ShieldCapVisualSyncService.BackShieldPreference.PRIORITY_REFRESH
        );
    }
}
