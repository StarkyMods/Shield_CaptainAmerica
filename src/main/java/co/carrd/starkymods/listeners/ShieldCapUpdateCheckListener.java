package co.carrd.starkymods.listeners;

import co.carrd.starkymods.update.ShieldCapUpdateCheckService;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCapUpdateCheckListener {
    private static final Set<UUID> CHECKED_PLAYERS = ConcurrentHashMap.newKeySet();

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
        if (!CHECKED_PLAYERS.add(playerUuid)) {
            return;
        }

        ShieldCapUpdateCheckService.checkForPlayer(world, playerUuid);
    }

    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (event == null) {
            return;
        }

        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef != null && playerRef.getUuid() != null) {
            CHECKED_PLAYERS.remove(playerRef.getUuid());
        }
    }
}
