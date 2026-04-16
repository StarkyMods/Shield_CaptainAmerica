package co.carrd.starkymods.listeners;

import co.carrd.starkymods.update.ShieldCapUpdateCheckService;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

public final class ShieldCapUpdateCheckListener {
    private ShieldCapUpdateCheckListener() {
    }

    public static void onPlayerReady(PlayerReadyEvent event) {
        if (event == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        UUID playerUuid = ((CommandSender) player).getUuid();
        if (playerUuid == null) {
            return;
        }

        ShieldCapUpdateCheckService.checkForPlayer(world, playerUuid);
    }
}
