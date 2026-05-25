package co.carrd.starkymods.commands;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

public class ShieldCapCraftCommand extends CommandBase {

    public ShieldCapCraftCommand() {
        super("capshieldcraft", "Toggle Captain America's Shield crafting globally.");
        this.requirePermission("starkymods.shieldcap.command.capshieldcraft");
    }

    @Override
    protected void executeSync(CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Only players can use this command."));
            return;
        }

        CommandSender sender = context.sender();
        if (!PermissionsModule.get().hasPermission(sender.getUuid(), "*")) {
            context.sendMessage(Message.raw("You must be OP to use this command."));
            return;
        }

        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        plugin.toggleShieldCraftBlocked();
        context.sendMessage(Message.raw(plugin.getCraftToggleStatusMessage()));
    }
}
