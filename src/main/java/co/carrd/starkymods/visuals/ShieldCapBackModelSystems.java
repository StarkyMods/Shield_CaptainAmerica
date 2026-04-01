package co.carrd.starkymods.visuals;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticType;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkin;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class ShieldCapBackModelSystems {
    private static final String BACK_ATTACHMENT_MODEL =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericaback.blockymodel";
    private static final String BACK_ATTACHMENT_TEXTURE =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericaback_attachment.png";

    private ShieldCapBackModelSystems() {
    }

    public static void registerSystems(JavaPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new Tick());
    }

    public static final class Tick extends EntityTickingSystem<EntityStore> {
        private static final Query<EntityStore> QUERY = Query.and(
                ShieldCapBackStateComponent.getComponentType(),
                Player.getComponentType(),
                PlayerSkinComponent.getComponentType(),
                ModelComponent.getComponentType()
        );

        @Override
        public void tick(float delta,
                         int entityIndex,
                         @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            ShieldCapBackStateComponent state =
                    chunk.getComponent(entityIndex, ShieldCapBackStateComponent.getComponentType());
            if (state == null || !state.consumeDirty()) {
                return;
            }

            PlayerSkinComponent playerSkinComponent =
                    chunk.getComponent(entityIndex, PlayerSkinComponent.getComponentType());
            if (playerSkinComponent == null) {
                return;
            }

            Player player = chunk.getComponent(entityIndex, Player.getComponentType());

            Model baseModel = CosmeticsModule.get().createModel(playerSkinComponent.getPlayerSkin());
            if (baseModel == null) {
                return;
            }

            if (!state.shouldShowBackShield()) {
                state.setPendingApply(false);
                playerSkinComponent.setNetworkOutdated();
                chunk.setComponent(entityIndex, ModelComponent.getComponentType(), new ModelComponent(baseModel));
                return;
            }

            if (!state.isPendingApply()) {
                playerSkinComponent.setNetworkOutdated();
                state.setPendingApply(true);
                state.rebuild();
                return;
            }

            Model shieldModel = buildShieldCapModel(player, baseModel);
            state.setPendingApply(false);
            chunk.setComponent(entityIndex, ModelComponent.getComponentType(), new ModelComponent(shieldModel));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        private Model buildShieldCapModel(Player player, Model baseModel) {
            Model appearanceModel = rebuildHytaleAppearanceModel(player, baseModel);
            List<ModelAttachment> attachments = new ArrayList<>();
            for (ModelAttachment attachment : appearanceModel.getAttachments()) {
                if (!isShieldCapAttachment(attachment)) {
                    attachments.add(attachment);
                }
            }

            attachments.add(new ModelAttachment(
                    BACK_ATTACHMENT_MODEL,
                    BACK_ATTACHMENT_TEXTURE,
                    null,
                    null,
                    1.0
            ));

            return new Model(
                    appearanceModel.getModelAssetId(),
                    appearanceModel.getScale(),
                    appearanceModel.getRandomAttachmentIds(),
                    attachments.toArray(ModelAttachment[]::new),
                    appearanceModel.getBoundingBox(),
                    appearanceModel.getModel(),
                    appearanceModel.getTexture(),
                    appearanceModel.getGradientSet(),
                    appearanceModel.getGradientId(),
                    appearanceModel.getEyeHeight(),
                    appearanceModel.getCrouchOffset(),
                    getOptionalFloat(appearanceModel, "sittingOffset"),
                    getOptionalFloat(appearanceModel, "sleepingOffset"),
                    appearanceModel.getAnimationSetMap(),
                    appearanceModel.getCamera(),
                    appearanceModel.getLight(),
                    appearanceModel.getParticles(),
                    appearanceModel.getTrails(),
                    appearanceModel.getPhysicsValues(),
                    appearanceModel.getDetailBoxes(),
                    appearanceModel.getPhobia(),
                    appearanceModel.getPhobiaModelAssetId()
            );
        }

        private Model rebuildHytaleAppearanceModel(Player player, Model baseModel) {
            if (player == null) {
                return baseModel;
            }

            if (player.getPlayerRef() == null || player.getPlayerRef().getReference() == null) {
                return baseModel;
            }

            Store<EntityStore> store = player.getPlayerRef().getReference().getStore();
            if (store == null) {
                return baseModel;
            }

            PlayerSkinComponent playerSkinComponent =
                    store.getComponent(player.getPlayerRef().getReference(), PlayerSkinComponent.getComponentType());
            if (playerSkinComponent == null || playerSkinComponent.getPlayerSkin() == null) {
                return baseModel;
            }

            PlayerSkin skin = skinFromProtocol(playerSkinComponent.getPlayerSkin());
            CosmeticRegistry registry = CosmeticsModule.get().getRegistry();

            Model model = baseModel;
            List<ModelAttachment> attachments = new ArrayList<>();

            for (CosmeticType type : CosmeticType.values()) {
                PlayerSkin.PlayerSkinPartId partId = getSkinPartForType(type, skin);
                if (partId == null || partId.getAssetId() == null) {
                    continue;
                }

                Object asset = registry.getByType(type).get(partId.getAssetId());
                if (!(asset instanceof PlayerSkinPart part)) {
                    continue;
                }

                if (type == CosmeticType.BODY_CHARACTERISTICS) {
                    model = applyBodyCharacteristic(model, part, partId);
                    continue;
                }

                ModelAttachment attachment = createSkinAttachment(part, partId);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }

            return new Model(
                    model.getModelAssetId(),
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    attachments.toArray(ModelAttachment[]::new),
                    model.getBoundingBox(),
                    model.getModel(),
                    model.getTexture(),
                    model.getGradientSet(),
                    model.getGradientId(),
                    model.getEyeHeight(),
                    model.getCrouchOffset(),
                    getOptionalFloat(model, "sittingOffset"),
                    getOptionalFloat(model, "sleepingOffset"),
                    model.getAnimationSetMap(),
                    model.getCamera(),
                    model.getLight(),
                    model.getParticles(),
                    model.getTrails(),
                    model.getPhysicsValues(),
                    model.getDetailBoxes(),
                    model.getPhobia(),
                    model.getPhobiaModelAssetId()
            );
        }

        private Model applyBodyCharacteristic(Model model,
                                              PlayerSkinPart part,
                                              PlayerSkin.PlayerSkinPartId partId) {
            return new Model(
                    model.getModelAssetId(),
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    model.getAttachments(),
                    model.getBoundingBox(),
                    part.getModel(),
                    part.getGreyscaleTexture(),
                    part.getGradientSet(),
                    partId.getTextureId(),
                    model.getEyeHeight(),
                    model.getCrouchOffset(),
                    getOptionalFloat(model, "sittingOffset"),
                    getOptionalFloat(model, "sleepingOffset"),
                    model.getAnimationSetMap(),
                    model.getCamera(),
                    model.getLight(),
                    model.getParticles(),
                    model.getTrails(),
                    model.getPhysicsValues(),
                    model.getDetailBoxes(),
                    model.getPhobia(),
                    model.getPhobiaModelAssetId()
            );
        }

        private ModelAttachment createSkinAttachment(PlayerSkinPart part, PlayerSkin.PlayerSkinPartId partId) {
            String model;
            String texture;
            String gradientSet = null;
            String gradientId = null;

            if (part.getVariants() != null && !part.getVariants().isEmpty() && partId.getVariantId() != null) {
                PlayerSkinPart.Variant variant = part.getVariants().get(partId.getVariantId());
                if (variant == null) {
                    return null;
                }

                model = variant.getModel();
                if (variant.getTextures() != null && !variant.getTextures().isEmpty()) {
                    PlayerSkinPartTexture partTexture = variant.getTextures().get(partId.getTextureId());
                    if (partTexture == null) {
                        return null;
                    }
                    texture = partTexture.getTexture();
                } else {
                    texture = variant.getGreyscaleTexture();
                    gradientSet = part.getGradientSet();
                    gradientId = partId.getTextureId();
                }
            } else {
                model = part.getModel();
                if (part.getTextures() != null && !part.getTextures().isEmpty()) {
                    PlayerSkinPartTexture partTexture = part.getTextures().get(partId.getTextureId());
                    if (partTexture == null) {
                        return null;
                    }
                    texture = partTexture.getTexture();
                } else {
                    texture = part.getGreyscaleTexture();
                    gradientSet = part.getGradientSet();
                    gradientId = partId.getTextureId();
                }
            }

            return new ModelAttachment(model, texture, gradientSet, gradientId, 1.0);
        }

        private PlayerSkin skinFromProtocol(com.hypixel.hytale.protocol.PlayerSkin protocolSkin) {
            return new PlayerSkin(
                    protocolSkin.bodyCharacteristic,
                    protocolSkin.underwear,
                    protocolSkin.face,
                    protocolSkin.ears,
                    protocolSkin.mouth,
                    protocolSkin.eyes,
                    protocolSkin.facialHair,
                    protocolSkin.haircut,
                    protocolSkin.eyebrows,
                    protocolSkin.pants,
                    protocolSkin.overpants,
                    protocolSkin.undertop,
                    protocolSkin.overtop,
                    protocolSkin.shoes,
                    protocolSkin.headAccessory,
                    protocolSkin.faceAccessory,
                    protocolSkin.earAccessory,
                    protocolSkin.skinFeature,
                    protocolSkin.gloves,
                    protocolSkin.cape
            );
        }

        private PlayerSkin.PlayerSkinPartId getSkinPartForType(CosmeticType type, PlayerSkin skin) {
            return switch (type) {
                case EMOTES, EMOTES_INGAME, GRADIENT_SETS, EYE_COLORS, SKIN_TONES -> null;
                case BODY_CHARACTERISTICS -> skin.getBodyCharacteristic();
                case UNDERWEAR -> skin.getUnderwear();
                case EYEBROWS -> skin.getEyebrows();
                case EYES -> skin.getEyes();
                case FACIAL_HAIR -> skin.getFacialHair();
                case PANTS -> skin.getPants();
                case OVERPANTS -> skin.getOverpants();
                case UNDERTOPS -> skin.getUndertop();
                case OVERTOPS -> skin.getOvertop();
                case HAIRCUTS -> skin.getHaircut();
                case SHOES -> skin.getShoes();
                case HEAD_ACCESSORY -> skin.getHeadAccessory();
                case FACE_ACCESSORY -> skin.getFaceAccessory();
                case EAR_ACCESSORY -> skin.getEarAccessory();
                case GLOVES -> skin.getGloves();
                case CAPES -> skin.getCape();
                case SKIN_FEATURES -> skin.getSkinFeature();
                case EARS -> new PlayerSkin.PlayerSkinPartId(skin.getEars(), skin.getBodyCharacteristic().textureId, null);
                case FACE -> new PlayerSkin.PlayerSkinPartId(skin.getFace(), skin.getBodyCharacteristic().textureId, null);
                case MOUTHS -> new PlayerSkin.PlayerSkinPartId(skin.getMouth(), skin.getBodyCharacteristic().textureId, null);
                case null -> null;
                default -> null;
            };
        }

        private boolean isShieldCapAttachment(ModelAttachment attachment) {
            return attachment != null
                    && BACK_ATTACHMENT_MODEL.equals(attachment.getModel())
                    && BACK_ATTACHMENT_TEXTURE.equals(attachment.getTexture());
        }

        private float getOptionalFloat(Model model, String fieldName) {
            try {
                Field field = Model.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(model);
                return value instanceof Float floatValue ? floatValue : 0.0f;
            } catch (ReflectiveOperationException ignored) {
                return 0.0f;
            }
        }
    }
}
