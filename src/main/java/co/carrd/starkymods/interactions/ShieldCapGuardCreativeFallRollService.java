package co.carrd.starkymods.interactions;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

final class ShieldCapGuardCreativeFallRollService {
    private static final double MIN_ROLL_FALL_DISTANCE = 8.0d;
    private static final Map<UUID, Boolean> LAST_ON_GROUND = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> MAX_AIR_FALL_DISTANCE = new ConcurrentHashMap<>();

    private ShieldCapGuardCreativeFallRollService() {
    }

    static void tickStore(Store<EntityStore> store) {
        if (store == null) {
            return;
        }

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> chunkConsumer = (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
                PlayerRef playerIdRef = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
                if (player == null || playerIdRef == null || playerIdRef.getUuid() == null) {
                    continue;
                }

                UUID playerUuid = playerIdRef.getUuid();
                double currentFallDistance = player.getCurrentFallDistance();
                MovementStatesComponent movementComponent =
                        commandBuffer.getComponent(playerRef,
                                com.hypixel.hytale.server.core.modules.entity.EntityModule.get().getMovementStatesComponentType());
                MovementStates movementStates = movementComponent == null ? null : movementComponent.getMovementStates();
                boolean onGround = movementStates != null && movementStates.onGround;
                boolean wasOnGround = LAST_ON_GROUND.getOrDefault(playerUuid, Boolean.TRUE);
                LAST_ON_GROUND.put(playerUuid, onGround);

                if (!onGround) {
                    MAX_AIR_FALL_DISTANCE.merge(playerUuid, currentFallDistance, Math::max);
                }

                if (player.getGameMode() != GameMode.Creative) {
                    MAX_AIR_FALL_DISTANCE.remove(playerUuid);
                    continue;
                }
                if (!onGround || wasOnGround) {
                    continue;
                }

                double landedFallDistance =
                        Math.max(currentFallDistance, MAX_AIR_FALL_DISTANCE.getOrDefault(playerUuid, 0.0d));
                MAX_AIR_FALL_DISTANCE.remove(playerUuid);

                if (landedFallDistance < MIN_ROLL_FALL_DISTANCE) {
                    continue;
                }
                if (ShieldCapGuardFallDamageReductionSystem.isTemporaryProtectionActive(playerUuid)) {
                    continue;
                }

                InteractionManager interactionManager =
                        commandBuffer.getComponent(playerRef, InteractionModule.get().getInteractionManagerComponent());
                if (!ShieldCapGuardFallDamageReductionSystem.isGuardWieldActive(interactionManager)) {
                    continue;
                }

                ShieldCapGuardFallDamageReductionSystem.triggerFallDamageRoll(playerRef, commandBuffer, interactionManager);
            }
        };

        store.forEachChunk(Player.getComponentType(), chunkConsumer);
    }
}
