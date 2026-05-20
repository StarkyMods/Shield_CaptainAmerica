package co.carrd.starkymods.interactions;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCapKickPushKnockbackImmunitySystem extends DamageEventSystem {
    private static final String KICK_PUSH_INTERACTION_ID = "ShieldCap_Kick_Push";
    private static final long IMMUNITY_WINDOW_MS = 2_000L;
    private static final Map<UUID, Long> IMMUNITY_UNTIL_MS = new ConcurrentHashMap<>();

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

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID playerUuid = resolvePlayerUuid(playerRef, commandBuffer);

        InteractionManager interactionManager =
                commandBuffer.getComponent(playerRef, InteractionModule.get().getInteractionManagerComponent());
        if (!isKickPushActive(interactionManager) && !isImmunityActive(playerUuid)) {
            return;
        }

        damage.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
    }

    public static void arm(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> commandBuffer) {
        UUID playerUuid = resolvePlayerUuid(entityRef, commandBuffer);
        if (playerUuid == null) {
            return;
        }

        IMMUNITY_UNTIL_MS.put(playerUuid, System.currentTimeMillis() + IMMUNITY_WINDOW_MS);
    }

    public static void tickStore(Store<EntityStore> store) {
        if (store == null || IMMUNITY_UNTIL_MS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        IMMUNITY_UNTIL_MS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < now);
        if (IMMUNITY_UNTIL_MS.isEmpty()) {
            return;
        }

        store.forEachChunk(Player.getComponentType(), (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                UUID playerUuid = resolvePlayerUuid(playerRef, commandBuffer);
                if (!isImmunityActive(playerUuid)) {
                    continue;
                }

                KnockbackComponent knockbackComponent =
                        commandBuffer.getComponent(playerRef, KnockbackComponent.getComponentType());
                if (knockbackComponent == null) {
                    continue;
                }

                knockbackComponent.setVelocity(new Vector3d());
                knockbackComponent.setDuration(0.0f);
                knockbackComponent.setTimer(0.0f);
                commandBuffer.tryRemoveComponent(playerRef, KnockbackComponent.getComponentType());
            }
        });
    }

    static boolean isKickPushActive(InteractionManager interactionManager) {
        if (interactionManager == null) {
            return false;
        }

        Boolean active = interactionManager.forEachInteraction(
                (chain, interaction, acc) -> {
                    if (acc) {
                        return true;
                    }
                    String id = interaction.getId();
                    return id != null
                            && (KICK_PUSH_INTERACTION_ID.equals(id) || id.contains(KICK_PUSH_INTERACTION_ID));
                },
                Boolean.FALSE
        );

        return Boolean.TRUE.equals(active);
    }

    private static boolean isImmunityActive(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long untilMs = IMMUNITY_UNTIL_MS.get(playerUuid);
        if (untilMs == null) {
            return false;
        }
        if (untilMs < now) {
            IMMUNITY_UNTIL_MS.remove(playerUuid, untilMs);
            return false;
        }
        return true;
    }

    private static UUID resolvePlayerUuid(Ref<EntityStore> playerRef,
                                          CommandBuffer<EntityStore> commandBuffer) {
        if (playerRef == null || !playerRef.isValid() || commandBuffer == null) {
            return null;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }
}
