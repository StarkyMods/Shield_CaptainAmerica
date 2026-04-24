package co.carrd.starkymods.visuals;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;

public final class ShieldCapBackShieldDamageReductionSystem extends DamageEventSystem {
    private static final float BACK_SHIELD_DAMAGE_MULTIPLIER = 0.25f;

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                Player.getComponentType(),
                ShieldCapBackStateComponent.getComponentType()
        );
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage == null || damage.isCancelled()) {
            return;
        }

        ShieldCapBackStateComponent backState =
                chunk.getComponent(index, ShieldCapBackStateComponent.getComponentType());
        if (backState == null || !backState.shouldShowBackShield()) {
            return;
        }

        float currentAmount = damage.getAmount();
        if (currentAmount <= 0.0f) {
            return;
        }

        damage.setAmount(currentAmount * BACK_SHIELD_DAMAGE_MULTIPLIER);
    }
}
