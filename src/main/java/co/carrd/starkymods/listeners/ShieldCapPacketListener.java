package co.carrd.starkymods.listeners;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import co.carrd.starkymods.config.ShieldCapConfigManager;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.SendWindowAction;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShieldCapPacketListener {
    private static final String CRAFT_DISABLED_MESSAGE = "Cap's Shield crafting is currently disabled.";
    private static final long CRAFT_DISABLED_COOLDOWN_MS = 2000L;
    private static final Map<UUID, Long> disabledMessageCooldown = new HashMap<>();

    private ShieldCapPacketListener() {
    }

    public static void register() {
        PacketAdapters.registerInbound((PlayerPacketFilter) (player, packet) -> {
            boolean isOp = PermissionsModule.get().hasPermission(player.getUuid(), "*");
            StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
            if (plugin != null) {
                plugin.pollCraftHotReload(player, isOp);
                plugin.pollDamageHotReload(player, isOp);
            }

            if (!(packet instanceof SendWindowAction sendWindowAction)) {
                return false;
            }

            if (!(sendWindowAction.action instanceof CraftRecipeAction recipeAction)) {
                return false;
            }

            if (!ShieldCapConfigManager.isShieldRecipeId(recipeAction.recipeId)) {
                return false;
            }

            if (ShieldCapConfigManager.isCraftingAllowed() || isOp) {
                return false;
            }

            sendWithCooldown(player.getUuid(), player);
            return true;
        });
    }

    private static void sendWithCooldown(UUID uuid, com.hypixel.hytale.server.core.universe.PlayerRef player) {
        long now = System.currentTimeMillis();
        long last = disabledMessageCooldown.getOrDefault(uuid, 0L);

        if (now - last <= CRAFT_DISABLED_COOLDOWN_MS) {
            return;
        }

        player.sendMessage(Message.raw(CRAFT_DISABLED_MESSAGE));
        disabledMessageCooldown.put(uuid, now);
    }
}
