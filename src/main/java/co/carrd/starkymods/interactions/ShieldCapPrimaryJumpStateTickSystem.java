package co.carrd.starkymods.interactions;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ShieldCapPrimaryJumpStateTickSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float deltaTime, int tick, Store<EntityStore> store) {
        ShieldCapPrimaryJumpHitCooldown.tickStore(store);
    }
}
