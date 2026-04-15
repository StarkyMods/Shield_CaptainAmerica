package co.carrd.starkymods.visuals;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.util.Set;

public final class ShieldCapBackShieldDamageReductionSystem extends DamageEventSystem {
    private static final float BACK_SHIELD_DAMAGE_MULTIPLIER = 0.25f;
    private static final String BACK_SHIELD_HIT_SOUND_ID = "SFX_ShieldCap_BackShieldHit";
    private static final double BACK_HIT_DOT_THRESHOLD = -0.25d;

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
        if (!isBackHit(chunk.getReferenceTo(index), store, commandBuffer, damage)) {
            return;
        }

        float currentAmount = damage.getAmount();
        if (currentAmount <= 0.0f) {
            return;
        }

        damage.setAmount(currentAmount * BACK_SHIELD_DAMAGE_MULTIPLIER);
        playBackShieldHitSound(chunk.getReferenceTo(index), store, commandBuffer);
    }

    private static void playBackShieldHitSound(Ref<EntityStore> playerRef,
                                               Store<EntityStore> store,
                                               CommandBuffer<EntityStore> commandBuffer) {
        if (playerRef == null || !playerRef.isValid() || store == null || commandBuffer == null || store.getExternalData() == null) {
            return;
        }

        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(BACK_SHIELD_HIT_SOUND_ID, SoundEvent.EMPTY_ID);
        if (soundIndex == SoundEvent.EMPTY_ID) {
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(playerRef, EntityModule.get().getTransformComponentType());
        World world = store.getExternalData().getWorld();
        if (transform == null || transform.getPosition() == null || world == null) {
            return;
        }

        PlaySoundEvent3D packet = new PlaySoundEvent3D(
                soundIndex,
                SoundCategory.SFX,
                PositionUtil.toPositionPacket(transform.getPosition()),
                1.0f,
                1.0f
        );
        world.getNotificationHandler().sendPacketIfChunkLoaded(
                packet,
                (int) Math.floor(transform.getPosition().x),
                (int) Math.floor(transform.getPosition().z)
        );
    }

    private static boolean isBackHit(Ref<EntityStore> playerRef,
                                     Store<EntityStore> store,
                                     CommandBuffer<EntityStore> commandBuffer,
                                     Damage damage) {
        if (playerRef == null || !playerRef.isValid() || store == null || commandBuffer == null || damage == null) {
            return false;
        }

        Ref<EntityStore> sourceRef = resolveDamageSourceRef(damage);
        if (sourceRef == null || !sourceRef.isValid() || sourceRef.getStore() != store) {
            return false;
        }

        TransformComponent playerTransform =
                commandBuffer.getComponent(playerRef, EntityModule.get().getTransformComponentType());
        TransformComponent sourceTransform =
                commandBuffer.getComponent(sourceRef, EntityModule.get().getTransformComponentType());
        if (playerTransform == null || sourceTransform == null || playerTransform.getPosition() == null || sourceTransform.getPosition() == null) {
            return false;
        }

        Transform playerWorldTransform = playerTransform.getTransform();
        Vector3d playerForward = playerWorldTransform == null ? null : playerWorldTransform.getDirection();
        if (playerForward == null) {
            return false;
        }

        double forwardX = playerForward.x;
        double forwardZ = playerForward.z;
        double forwardLength = Math.sqrt((forwardX * forwardX) + (forwardZ * forwardZ));
        if (forwardLength < 1.0e-6d) {
            return false;
        }
        forwardX /= forwardLength;
        forwardZ /= forwardLength;

        Vector3d playerPos = playerTransform.getPosition();
        Vector3d sourcePos = sourceTransform.getPosition();
        double toSourceX = sourcePos.x - playerPos.x;
        double toSourceZ = sourcePos.z - playerPos.z;
        double sourceDistance = Math.sqrt((toSourceX * toSourceX) + (toSourceZ * toSourceZ));
        if (sourceDistance < 1.0e-6d) {
            return false;
        }
        toSourceX /= sourceDistance;
        toSourceZ /= sourceDistance;

        double dot = (forwardX * toSourceX) + (forwardZ * toSourceZ);
        return dot <= BACK_HIT_DOT_THRESHOLD;
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
