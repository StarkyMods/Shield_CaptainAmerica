package co.carrd.starkymods.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.bson.BsonDocument;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class ShieldCapThrowHomingLaunchConfigProjectile extends SimpleInstantInteraction {
    private static final String NORMAL_PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig";
    private static final String VIBRANIUM_PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig_Silver";
    private static final String CARTER_PROJECTILE_CONFIG_ID = "ShieldCap_ProjectileConfig_Carter";
    private static final String THROWN_ITEM_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String VARIANT_METADATA_KEY = "ShieldCapVariant";
    private static final String VIBRANIUM_VARIANT_VALUE = "Vibranium";
    private static final String CARTER_VARIANT_VALUE = "Carter";

    @Nonnull
    public static final BuilderCodec<ShieldCapThrowHomingLaunchConfigProjectile> CODEC =
            BuilderCodec
                    .builder(
                            ShieldCapThrowHomingLaunchConfigProjectile.class,
                            ShieldCapThrowHomingLaunchConfigProjectile::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .appendInherited(
                            new KeyedCodec<>("Config", Codec.STRING),
                            (o, i) -> o.config = i,
                            o -> o.config,
                            (o, p) -> o.config = p.config
                    )
                    .addValidator(ProjectileConfig.VALIDATOR_CACHE.getValidator().late())
                    .add()
                    .build();

    protected String config;

    @Nullable
    private ProjectileConfig getConfig(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ownerRef) {
        return ProjectileConfig.getAssetMap().getAsset(resolveConfigId(commandBuffer, ownerRef));
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ownerRef = context.getEntity();
        if (commandBuffer == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        ProjectileConfig projectileConfig = getConfig(commandBuffer, ownerRef);
        if (projectileConfig == null) {
            return;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(ownerRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }

        UUID ownerUuid = uuidComponent.getUuid();
        Transform look = TargetUtil.getLook(ownerRef, commandBuffer);
        Vector3d position = look.getPosition();
        Vector3d direction = look.getDirection();

        Ref<EntityStore> projectileRef =
                ProjectileModule.get().spawnProjectile(null, ownerRef, commandBuffer, projectileConfig, position, direction);
        if (projectileRef == null) {
            return;
        }

        ShieldCapThrowHomingService.markProjectile(ownerUuid, ownerRef, projectileRef);
    }

    private String resolveConfigId(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ownerRef) {
        if (NORMAL_PROJECTILE_CONFIG_ID.equals(config)) {
            String variant = findThrownShieldVariant(commandBuffer, ownerRef);
            if (VIBRANIUM_VARIANT_VALUE.equals(variant)) {
                return VIBRANIUM_PROJECTILE_CONFIG_ID;
            }
            if (CARTER_VARIANT_VALUE.equals(variant)) {
                return CARTER_PROJECTILE_CONFIG_ID;
            }
        }
        return config;
    }

    private String findThrownShieldVariant(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ownerRef) {
        if (commandBuffer == null || ownerRef == null || !ownerRef.isValid()) {
            return null;
        }

        Player player = commandBuffer.getComponent(ownerRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return null;
        }

        Inventory inventory = player.getInventory();
        String variant = findThrownShieldVariant(inventory.getHotbar());
        if (variant != null) {
            return variant;
        }
        variant = findThrownShieldVariant(inventory.getUtility());
        if (variant != null) {
            return variant;
        }
        variant = findThrownShieldVariant(inventory.getStorage());
        if (variant != null) {
            return variant;
        }
        variant = findThrownShieldVariant(inventory.getBackpack());
        if (variant != null) {
            return variant;
        }
        return findThrownShieldVariant(inventory.getTools());
    }

    private String findThrownShieldVariant(ItemContainer container) {
        if (container == null) {
            return null;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            String variant = getThrownShieldVariant(container.getItemStack(slot));
            if (variant != null) {
                return variant;
            }
        }
        return null;
    }

    private String getThrownShieldVariant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()
                || !(itemId.equals(THROWN_ITEM_ID) || itemId.endsWith("." + THROWN_ITEM_ID) || itemId.contains(THROWN_ITEM_ID))) {
            return null;
        }

        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || !metadata.containsKey(VARIANT_METADATA_KEY)) {
            return null;
        }

        try {
            return metadata.getString(VARIANT_METADATA_KEY, new BsonString("")).getValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
