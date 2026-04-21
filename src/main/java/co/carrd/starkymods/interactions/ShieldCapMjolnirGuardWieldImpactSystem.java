package co.carrd.starkymods.interactions;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;

public final class ShieldCapMjolnirGuardWieldImpactSystem extends DamageEventSystem {
    private static final String MJOLNIR_ITEM_ID = "Weapon_Mjolnir_Starky";
    private static final String ROOT_ID = "Root_ShieldCap_Mjolnir_Guard_Wield_Impact_Shockwave";
    private static final InteractionType SHOCKWAVE_LANE = InteractionType.Ability2;

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
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

        Ref<EntityStore> defenderRef = chunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        InteractionManager defenderInteractionManager =
                commandBuffer.getComponent(defenderRef, InteractionModule.get().getInteractionManagerComponent());
        if (!ShieldCapGuardFallDamageReductionSystem.isGuardWieldActive(defenderInteractionManager)) {
            return;
        }

        Ref<EntityStore> attackerRef = resolveDamageSourceRef(damage);
        if (attackerRef == null || !attackerRef.isValid() || attackerRef.getStore() != store) {
            return;
        }
        if (!attackerHasMjolnirInMainHand(attackerRef, commandBuffer)) {
            return;
        }

        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(ROOT_ID);
        if (rootInteraction == null || defenderInteractionManager == null) {
            return;
        }

        try {
            InteractionContext context =
                    InteractionContext.forInteraction(defenderInteractionManager, defenderRef, SHOCKWAVE_LANE, commandBuffer);
            defenderInteractionManager.startChain(defenderRef, commandBuffer, SHOCKWAVE_LANE, context, rootInteraction);
        } catch (Throwable ignored) {
            // Best-effort only. Damage should still proceed normally.
        }
    }

    private static boolean attackerHasMjolnirInMainHand(Ref<EntityStore> attackerRef,
                                                         CommandBuffer<EntityStore> commandBuffer) {
        ItemStack heldItem = InventoryComponent.getItemInHand(commandBuffer, attackerRef);
        if (heldItem == null || heldItem.isEmpty()) {
            return false;
        }

        String itemId = heldItem.getItemId();
        return itemId != null
                && (itemId.equals(MJOLNIR_ITEM_ID)
                || itemId.endsWith("." + MJOLNIR_ITEM_ID)
                || itemId.contains(MJOLNIR_ITEM_ID));
    }

    private static Ref<EntityStore> resolveDamageSourceRef(Damage damage) {
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> ref = entitySource.getRef();
            return ref != null && ref.isValid() ? ref : null;
        }
        return null;
    }
}
