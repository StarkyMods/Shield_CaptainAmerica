package co.carrd.starkymods.interactions;

import javax.annotation.Nullable;

import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class ShieldCapReturnKickInputService {
    private EventRegistration<Void, PlayerMouseButtonEvent> mouseButtonRegistration;
    private EventRegistration<Void, PlayerDisconnectEvent> disconnectRegistration;

    public void register(JavaPlugin plugin) {
        mouseButtonRegistration =
                plugin.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, this::handleMouseButton);
        disconnectRegistration =
                plugin.getEventRegistry().register(PlayerDisconnectEvent.class, this::handleDisconnect);
    }

    public void shutdown() {
        if (mouseButtonRegistration != null) {
            mouseButtonRegistration.unregister();
            mouseButtonRegistration = null;
        }
        if (disconnectRegistration != null) {
            disconnectRegistration.unregister();
            disconnectRegistration = null;
        }
    }

    private void handleMouseButton(PlayerMouseButtonEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        MouseButtonEvent mouseButton = event.getMouseButton();
        if (mouseButton == null
                || mouseButton.mouseButtonType != MouseButtonType.Left
                || mouseButton.state != MouseButtonState.Pressed) {
            return;
        }

        PlayerRef playerRef = event.getPlayerRefComponent();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        if (ShieldCapThrowHomingService.queueReturnKickIfEligible(playerRef)) {
            event.setCancelled(true);
        }
    }

    private void handleDisconnect(PlayerDisconnectEvent event) {
        if (event == null || event.getPlayerRef() == null) {
            return;
        }
        ShieldCapThrowHomingService.clearPendingReturnKickRequest(event.getPlayerRef().getUuid());
    }
}
