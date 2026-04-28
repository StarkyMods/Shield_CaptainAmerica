package co.carrd.starkymods.visuals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ShieldCapBackModelSystems {
    private static final String BACK_ATTACHMENT_MODEL =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericaback.blockymodel";
    private static final String BACK_ATTACHMENT_CAPE_MODEL =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericaback_cape.blockymodel";
    private static final String BACK_ATTACHMENT_TEXTURE =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamerica.png";
    private static final String BACK_ATTACHMENT_VIBRANIUM_TEXTURE =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericasilver.png";
    private static final String BACK_ATTACHMENT_CARTER_TEXTURE =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericacarter.png";
    private static final String LEGACY_BACK_ATTACHMENT_TEXTURE =
            "Items/Weapons/StarkyMods/starkyshieldcaptainamericaback_attachment.png";
    private static final String SIMPLIFIED_HAIRCUT_MODEL =
            "Characters/Haircuts/HairbaseGeneric.blockymodel";
    private static final String LOG_PREFIX = "[ShieldCapBackModelDebug] ";
    private static final boolean DEBUG_VISUALS = false;

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
            if (state == null) {
                return;
            }

            ModelComponent currentModelComponent =
                    chunk.getComponent(entityIndex, ModelComponent.getComponentType());
            if (state.shouldShowBackShield()
                    && !state.isPendingApply()
                    && currentModelComponent != null
                    && !hasShieldCapAttachment(currentModelComponent.getModel())) {
                state.rebuild();
            }

            Model currentModel = currentModelComponent == null ? null : currentModelComponent.getModel();
            PlayerSkinComponent playerSkinComponent =
                    chunk.getComponent(entityIndex, PlayerSkinComponent.getComponentType());
            if (playerSkinComponent == null) {
                return;
            }

            boolean dirty = state.consumeDirty();
            boolean hasActiveShieldAttachment = currentModel != null && hasShieldCapAttachment(currentModel);
            if (dirty && !state.shouldShowBackShield()) {
                state.setPendingApply(false);
                state.setPendingModelReset(false);
                state.clearPendingBaseModel();
                state.clearNaturalBaseModel();
                state.clearPreservedExtraAttachments();
                if (!hasActiveShieldAttachment) {
                    state.setAwaitingNaturalModel(false);
                    debug("skip remove back shield | player=" + resolvePlayerDebugId(chunk.getComponent(entityIndex, Player.getComponentType()))
                            + " | reason=no active back shield"
                            + " | currentModel=" + summarizeModel(currentModel));
                    return;
                }
            }

            Player player = chunk.getComponent(entityIndex, Player.getComponentType());
            String playerId = resolvePlayerDebugId(player);
            Model appearanceModel = createAppearanceModel(player, playerSkinComponent);

            if (!dirty) {
                rememberNaturalBaseModel(state, currentModel, appearanceModel);
                return;
            }

            rememberNaturalBaseModel(state, currentModel, appearanceModel);
            Model baseModel = selectInjectionBaseModel(currentModel, appearanceModel, state.getNaturalBaseModel());
            debug("tick dirty"
                    + " | player=" + playerId
                    + " | shouldShow=" + state.shouldShowBackShield()
                    + " | pendingApply=" + state.isPendingApply()
                    + " | skinAccessories=" + summarizeSkinAccessories(playerSkinComponent)
                    + " | currentModel=" + summarizeModel(currentModel)
                    + " | appearanceModel=" + summarizeModel(appearanceModel)
                    + " | baseModel=" + summarizeModel(baseModel));
            if (baseModel == null) {
                return;
            }

            if (!state.shouldShowBackShield()) {
                Model storedBaseModel = state.getPendingBaseModel();
                state.setAwaitingNaturalModel(true);
                state.clearNaturalBaseModel();
                Model directBaseModel;
                if (storedBaseModel != null) {
                    directBaseModel = removeShieldCapAttachment(storedBaseModel);
                } else if (hasActiveShieldAttachment) {
                    directBaseModel = removeShieldCapAttachment(currentModel);
                } else {
                    directBaseModel =
                            removeShieldCapAttachment(appearanceModel != null ? appearanceModel : baseModel);
                }
                debug("remove back shield | player=" + playerId + " | baseModel=" + summarizeModel(directBaseModel));
                chunk.setComponent(
                        entityIndex,
                        ModelComponent.getComponentType(),
                        new ModelComponent(removeShieldCapAttachment(directBaseModel))
                );
                playerSkinComponent.setNetworkOutdated();
                return;
            }

            if (state.isPendingModelReset()) {
                state.setPendingModelReset(false);
                state.setPendingApply(true);
                state.setAwaitingNaturalModel(false);
                state.clearNaturalBaseModel();
                state.clearPendingBaseModel();
                playerSkinComponent.setNetworkOutdated();
                state.rebuild();
                debug("request player model reset before back shield | player=" + playerId
                        + " | currentModel=" + summarizeModel(currentModel)
                        + " | appearanceModel=" + summarizeModel(appearanceModel));
                return;
            }

            Model preparedBaseModel = removeShieldCapAttachment(baseModel);
            boolean preserveRuntimeState = currentModel != null && preparedBaseModel == currentModel;
            Model storedBaseModel = preserveRuntimeState
                    ? cloneModelWithAttachments(preparedBaseModel, preparedBaseModel.getAttachments())
                    : preparedBaseModel;
            boolean useCapeShieldModel = hasCapeEquipped(playerSkinComponent);
            String backShieldVariant = state.getBackShieldVariant();
            Model shieldModel = buildShieldCapModel(
                    preparedBaseModel,
                    preserveRuntimeState,
                    useCapeShieldModel,
                    backShieldVariant
            );
            state.setPendingApply(false);
            state.setAwaitingNaturalModel(false);
            state.setPendingBaseModel(storedBaseModel);
            state.clearPreservedExtraAttachments();
            debug("apply back shield | player=" + playerId
                    + " | baseModel=" + summarizeModel(baseModel)
                    + " | currentModel=" + summarizeModel(currentModel)
                    + " | preparedBaseModel=" + summarizeModel(preparedBaseModel)
                    + " | useCapeShieldModel=" + useCapeShieldModel
                    + " | backShieldVariant=" + backShieldVariant
                    + " | preserveRuntimeState=" + preserveRuntimeState
                    + " | shieldModel=" + summarizeModel(shieldModel));
            chunk.setComponent(entityIndex, ModelComponent.getComponentType(), new ModelComponent(shieldModel));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        private Model buildShieldCapModel(Model baseModel) {
            return buildShieldCapModel(baseModel, false, false, "Normal");
        }

        private Model buildShieldCapModel(Model baseModel, boolean mutateInPlace) {
            return buildShieldCapModel(baseModel, mutateInPlace, false, "Normal");
        }

        private Model buildShieldCapModel(Model baseModel,
                                          boolean mutateInPlace,
                                          boolean useCapeShieldModel,
                                          String backShieldVariant) {
            List<ModelAttachment> attachments = new ArrayList<>();
            ModelAttachment[] baseAttachments = baseModel.getAttachments();
            if (baseAttachments != null) {
                for (ModelAttachment attachment : baseAttachments) {
                    if (!isShieldCapAttachment(attachment)) {
                        attachments.add(attachment);
                    }
                }
            }

            attachments.add(new ModelAttachment(
                    resolveBackAttachmentModel(useCapeShieldModel),
                    resolveBackAttachmentTexture(backShieldVariant),
                    null,
                    null,
                    1.0
            ));

            ModelAttachment[] updatedAttachments = attachments.toArray(ModelAttachment[]::new);
            if (mutateInPlace) {
                return setModelAttachmentsInPlace(baseModel, updatedAttachments);
            }
            return cloneModelWithAttachments(baseModel, updatedAttachments);
        }

        private String resolveBackAttachmentModel(boolean useCapeShieldModel) {
            return useCapeShieldModel ? BACK_ATTACHMENT_CAPE_MODEL : BACK_ATTACHMENT_MODEL;
        }

        private String resolveBackAttachmentTexture(String backShieldVariant) {
            if ("Vibranium".equals(backShieldVariant)) {
                return BACK_ATTACHMENT_VIBRANIUM_TEXTURE;
            }
            if ("Carter".equals(backShieldVariant)) {
                return BACK_ATTACHMENT_CARTER_TEXTURE;
            }
            return BACK_ATTACHMENT_TEXTURE;
        }

        private boolean hasCapeEquipped(PlayerSkinComponent playerSkinComponent) {
            if (playerSkinComponent == null || playerSkinComponent.getPlayerSkin() == null) {
                return false;
            }

            PlayerSkin playerSkin = skinFromProtocol(playerSkinComponent.getPlayerSkin());
            PlayerSkin.PlayerSkinPartId cape = playerSkin == null ? null : playerSkin.getCape();
            return cape != null && cape.getAssetId() != null && !cape.getAssetId().isBlank();
        }

        private Model createAppearanceModel(Player player, PlayerSkinComponent playerSkinComponent) {
            if (playerSkinComponent == null || playerSkinComponent.getPlayerSkin() == null) {
                return null;
            }

            Model skinBaseModel = CosmeticsModule.get().createModel(playerSkinComponent.getPlayerSkin());
            if (skinBaseModel == null) {
                return null;
            }

            return rebuildHytaleAppearanceModel(player, skinBaseModel);
        }

        private Model selectInjectionBaseModel(Model currentModel, Model appearanceModel) {
            return selectInjectionBaseModel(currentModel, appearanceModel, null);
        }

        private Model selectInjectionBaseModel(Model currentModel, Model appearanceModel, Model naturalBaseModel) {
            if (appearanceModel == null) {
                return naturalBaseModel != null ? removeShieldCapAttachment(naturalBaseModel) : removeShieldCapAttachment(currentModel);
            }
            if (currentModel == null) {
                return naturalBaseModel != null ? removeShieldCapAttachment(naturalBaseModel) : appearanceModel;
            }

            if (isLikelyUninitializedPlayerModel(currentModel, appearanceModel)) {
                return naturalBaseModel != null ? removeShieldCapAttachment(naturalBaseModel) : appearanceModel;
            }
            if (naturalBaseModel != null) {
                return removeShieldCapAttachment(naturalBaseModel);
            }

            return removeShieldCapAttachment(currentModel);
        }

        private void rememberNaturalBaseModel(ShieldCapBackStateComponent state, Model currentModel, Model appearanceModel) {
            if (state == null
                    || state.shouldShowBackShield()
                    || currentModel == null
                    || appearanceModel == null
                    || hasShieldCapAttachment(currentModel)) {
                return;
            }
            if (isLikelyUninitializedPlayerModel(currentModel, appearanceModel)) {
                return;
            }
            Model normalizedCurrent = removeShieldCapAttachment(currentModel);
            Model normalizedAppearance = removeShieldCapAttachment(appearanceModel);
            Model normalizedPendingBase = removeShieldCapAttachment(state.getPendingBaseModel());

            if (state.isAwaitingNaturalModel()) {
                if (normalizedPendingBase != null
                        && areModelsVisuallyEquivalent(normalizedCurrent, normalizedPendingBase)) {
                    return;
                }
            } else if (areModelsVisuallyEquivalent(normalizedCurrent, normalizedAppearance)) {
                return;
            }

            state.setNaturalBaseModel(normalizedCurrent);
            state.setAwaitingNaturalModel(false);
            state.clearPendingBaseModel();
            debug("captured natural base model | model=" + summarizeModel(state.getNaturalBaseModel()));
        }

        private Model selectPendingBaseModel(Model currentModel, Model fallbackBaseModel) {
            if (currentModel == null) {
                return fallbackBaseModel;
            }

            Model normalizedCurrent = removeShieldCapAttachment(currentModel);
            Model normalizedFallback = removeShieldCapAttachment(fallbackBaseModel);
            if (normalizedFallback != null && isEquivalentToAppearanceBase(normalizedCurrent, normalizedFallback)) {
                return normalizedCurrent;
            }
            return fallbackBaseModel;
        }

        private boolean isLikelyUninitializedPlayerModel(Model currentModel, Model appearanceModel) {
            if (currentModel == null || appearanceModel == null) {
                return currentModel == null;
            }

            if (!stringEquals(currentModel.getModelAssetId(), appearanceModel.getModelAssetId())
                    || !stringEquals(currentModel.getModel(), appearanceModel.getModel())) {
                return false;
            }

            boolean sameBaseVisual =
                    stringEquals(currentModel.getTexture(), appearanceModel.getTexture())
                            && stringEquals(currentModel.getGradientSet(), appearanceModel.getGradientSet())
                            && stringEquals(currentModel.getGradientId(), appearanceModel.getGradientId());
            if (sameBaseVisual && haveEquivalentAttachments(removeShieldCapAttachment(currentModel), appearanceModel)) {
                return false;
            }

            int currentAttachmentCount = countAttachments(removeShieldCapAttachment(currentModel).getAttachments());
            int appearanceAttachmentCount = countAttachments(appearanceModel.getAttachments());
            return currentAttachmentCount < appearanceAttachmentCount;
        }

        private Model removeShieldCapAttachment(Model model) {
            if (model == null || model.getAttachments() == null || model.getAttachments().length == 0) {
                return model;
            }

            List<ModelAttachment> attachments = new ArrayList<>();
            boolean removedAny = false;
            for (ModelAttachment attachment : model.getAttachments()) {
                if (isShieldCapAttachment(attachment)) {
                    removedAny = true;
                    continue;
                }
                attachments.add(attachment);
            }

            if (!removedAny) {
                return model;
            }

            return cloneModelWithAttachments(model, attachments.toArray(ModelAttachment[]::new));
        }

        private boolean isEquivalentToAppearanceBase(Model currentModel, Model appearanceModel) {
            if (currentModel == null || appearanceModel == null) {
                return false;
            }

            if (stringEquals(currentModel.getModelAssetId(), appearanceModel.getModelAssetId())
                    && stringEquals(currentModel.getModel(), appearanceModel.getModel())) {
                return true;
            }

            return haveEquivalentAttachments(currentModel, appearanceModel);
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
            String playerId = resolvePlayerDebugId(player);

            Model model = baseModel;
            List<ModelAttachment> attachments = new ArrayList<>();

            for (CosmeticType type : CosmeticType.values()) {
                PlayerSkin.PlayerSkinPartId partId = getSkinPartForType(type, skin);
                if (partId == null || partId.getAssetId() == null) {
                    continue;
                }

                Object asset = registry.getByType(type).get(partId.getAssetId());
                if (isAccessoryType(type)) {
                    debug("accessory registry asset"
                            + " | type=" + type
                            + " | requested=" + summarizePartId(partId)
                            + " | assetClass=" + (asset == null ? "null" : asset.getClass().getName())
                            + " | asset=" + asset);
                }
                if (!(asset instanceof PlayerSkinPart part)) {
                    continue;
                }

                if (type == CosmeticType.BODY_CHARACTERISTICS) {
                    model = applyBodyCharacteristic(model, part, partId);
                    continue;
                }

                ModelAttachment attachment;
                if (type == CosmeticType.HAIRCUTS && skin.getHeadAccessory() != null) {
                    attachment = createHaircutAttachment(baseModel.getAttachments(), part, partId, skin, registry);
                } else if (isAccessoryType(type)) {
                    attachment = createAccessoryAttachment(baseModel.getAttachments(), part, partId);
                } else {
                    attachment = createSkinAttachment(part, partId);
                }
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }

            debug("rebuild appearance"
                    + " | player=" + playerId
                    + " | skinAccessories=" + summarizeSkinAccessories(playerSkinComponent)
                    + " | baseAttachments=" + summarizeAttachments(baseModel.getAttachments())
                    + " | rebuiltAttachments=" + summarizeAttachments(attachments));

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

        private ModelAttachment[] capturePreservedExtraAttachments(Model currentModel, Model appearanceModel) {
            if (currentModel == null || currentModel.getAttachments() == null || currentModel.getAttachments().length == 0) {
                return null;
            }

            List<ModelAttachment> preserved = new ArrayList<>();
            ModelAttachment[] appearanceAttachments = appearanceModel == null ? null : appearanceModel.getAttachments();
            for (ModelAttachment currentAttachment : currentModel.getAttachments()) {
                if (currentAttachment == null || isShieldCapAttachment(currentAttachment)) {
                    continue;
                }
                if (containsAttachmentModel(appearanceAttachments, currentAttachment.getModel())) {
                    continue;
                }
                preserved.add(currentAttachment);
            }

            return preserved.isEmpty() ? null : preserved.toArray(ModelAttachment[]::new);
        }

        private Model mergePreservedExtraAttachments(Model model, ModelAttachment[] preservedAttachments) {
            if (model == null || preservedAttachments == null || preservedAttachments.length == 0) {
                return model;
            }

            List<ModelAttachment> attachments = new ArrayList<>();
            if (model.getAttachments() != null) {
                for (ModelAttachment attachment : model.getAttachments()) {
                    attachments.add(attachment);
                }
            }

            boolean addedAny = false;
            for (ModelAttachment preservedAttachment : preservedAttachments) {
                if (preservedAttachment == null
                        || isShieldCapAttachment(preservedAttachment)
                        || containsEquivalentAttachment(attachments.toArray(ModelAttachment[]::new), preservedAttachment)
                        || containsAttachmentModel(model.getAttachments(), preservedAttachment.getModel())) {
                    continue;
                }
                attachments.add(preservedAttachment);
                addedAny = true;
            }

            if (!addedAny) {
                return model;
            }

            return cloneModelWithAttachments(model, attachments.toArray(ModelAttachment[]::new));
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
            debug("createSkinAttachment start"
                    + " | requested=" + summarizePartId(partId)
                    + " | partId=" + part.getId()
                    + " | model=" + part.getModel()
                    + " | greyscale=" + part.getGreyscaleTexture()
                    + " | gradientSet=" + part.getGradientSet()
                    + " | textureKeys=" + summarizeKeys(part.getTextures())
                    + " | variantKeys=" + summarizeKeys(part.getVariants()));
            String model;
            String texture;
            String gradientSet = null;
            String gradientId = null;

            if (part.getVariants() != null && !part.getVariants().isEmpty() && partId.getVariantId() != null) {
                PlayerSkinPart.Variant variant = part.getVariants().get(partId.getVariantId());
                if (variant == null) {
                    debug("createSkinAttachment variant missing | requested=" + summarizePartId(partId));
                    return null;
                }

                model = variant.getModel();
                if (variant.getTextures() != null && !variant.getTextures().isEmpty()) {
                    PlayerSkinPartTexture partTexture = variant.getTextures().get(partId.getTextureId());
                    if (partTexture != null) {
                        texture = partTexture.getTexture();
                    } else if (variant.getGreyscaleTexture() != null && part.getGradientSet() != null) {
                        debug("createSkinAttachment variant texture fallback"
                                + " | requested=" + summarizePartId(partId)
                                + " | variantTextureKeys=" + summarizeKeys(variant.getTextures())
                                + " | greyscale=" + variant.getGreyscaleTexture()
                                + " | gradientSet=" + part.getGradientSet());
                        texture = variant.getGreyscaleTexture();
                        gradientSet = part.getGradientSet();
                        gradientId = partId.getTextureId();
                    } else {
                        debug("createSkinAttachment variant texture missing"
                                + " | requested=" + summarizePartId(partId)
                                + " | variantTextureKeys=" + summarizeKeys(variant.getTextures()));
                        return null;
                    }
                } else {
                    texture = variant.getGreyscaleTexture();
                    gradientSet = part.getGradientSet();
                    gradientId = partId.getTextureId();
                }
            } else {
                model = part.getModel();
                if (part.getTextures() != null && !part.getTextures().isEmpty()) {
                    PlayerSkinPartTexture partTexture = part.getTextures().get(partId.getTextureId());
                    if (partTexture != null) {
                        texture = partTexture.getTexture();
                    } else if (part.getGreyscaleTexture() != null && part.getGradientSet() != null) {
                        debug("createSkinAttachment texture fallback"
                                + " | requested=" + summarizePartId(partId)
                                + " | textureKeys=" + summarizeKeys(part.getTextures())
                                + " | greyscale=" + part.getGreyscaleTexture()
                                + " | gradientSet=" + part.getGradientSet());
                        texture = part.getGreyscaleTexture();
                        gradientSet = part.getGradientSet();
                        gradientId = partId.getTextureId();
                    } else {
                        debug("createSkinAttachment texture missing"
                                + " | requested=" + summarizePartId(partId)
                                + " | textureKeys=" + summarizeKeys(part.getTextures()));
                        return null;
                    }
                } else {
                    texture = part.getGreyscaleTexture();
                    gradientSet = part.getGradientSet();
                    gradientId = partId.getTextureId();
                }
            }

            debug("createSkinAttachment resolved"
                    + " | requested=" + summarizePartId(partId)
                    + " | model=" + model
                    + " | texture=" + texture
                    + " | gradientSet=" + gradientSet
                    + " | gradientId=" + gradientId);
            return new ModelAttachment(model, texture, gradientSet, gradientId, 1.0);
        }

        private ModelAttachment createAccessoryAttachment(ModelAttachment[] baseAttachments,
                                                          PlayerSkinPart part,
                                                          PlayerSkin.PlayerSkinPartId partId) {
            ModelAttachment rebuiltAttachment = createSkinAttachment(part, partId);
            ModelAttachment officialAttachment =
                    findMatchingBaseAttachment(baseAttachments, rebuiltAttachment, resolveSkinAttachmentModel(part, partId));
            debug("accessory attachment"
                    + " | requested=" + summarizePartId(partId)
                    + " | rebuilt=" + summarizeAttachment(rebuiltAttachment)
                    + " | officialMatch=" + summarizeAttachment(officialAttachment)
                    + " | baseAttachments=" + summarizeAttachments(baseAttachments));
            return officialAttachment != null ? officialAttachment : rebuiltAttachment;
        }

        private ModelAttachment createHaircutAttachment(ModelAttachment[] baseAttachments,
                                                        PlayerSkinPart part,
                                                        PlayerSkin.PlayerSkinPartId partId,
                                                        PlayerSkin skin,
                                                        CosmeticRegistry registry) {
            ModelAttachment rebuiltAttachment = createSkinAttachment(part, partId);
            ModelAttachment officialAttachment = findHaircutBaseAttachment(baseAttachments);
            boolean shouldHideHaircut = shouldHideHaircutAttachment(skin, registry);
            ModelAttachment simplifiedHaircutAttachment =
                    resolveSimplifiedHaircutAttachment(part, partId, skin, registry);
            ModelAttachment genericHaircutAttachment =
                    resolveGenericHaircutAttachment(part, partId, skin, registry);
            debug("haircut attachment"
                    + " | requested=" + summarizePartId(partId)
                    + " | rebuilt=" + summarizeAttachment(rebuiltAttachment)
                    + " | officialHaircut=" + summarizeAttachment(officialAttachment)
                    + " | shouldHideHaircut=" + shouldHideHaircut
                    + " | simplifiedHaircut=" + summarizeAttachment(simplifiedHaircutAttachment)
                    + " | genericHaircut=" + summarizeAttachment(genericHaircutAttachment)
                    + " | baseAttachments=" + summarizeAttachments(baseAttachments));
            if (officialAttachment != null) {
                return officialAttachment;
            }
            if (shouldHideHaircut) {
                return null;
            }
            if (simplifiedHaircutAttachment != null) {
                return simplifiedHaircutAttachment;
            }
            if (genericHaircutAttachment != null) {
                return genericHaircutAttachment;
            }
            return rebuiltAttachment;
        }

        private boolean shouldHideHaircutAttachment(PlayerSkin skin, CosmeticRegistry registry) {
            if (skin == null || registry == null || skin.getHeadAccessory() == null) {
                return false;
            }

            String headAccessoryId = skin.getHeadAccessory().getAssetId();
            HeadAccessoryRule headAccessoryRule =
                    OfficialCharacterCreatorData.getHeadAccessoryRule(headAccessoryId);
            return headAccessoryRule != null && headAccessoryRule.disablesHaircut;
        }

        private ModelAttachment resolveGenericHaircutAttachment(PlayerSkinPart haircutPart,
                                                               PlayerSkin.PlayerSkinPartId haircutPartId,
                                                               PlayerSkin skin,
                                                               CosmeticRegistry registry) {
            if (haircutPart == null || haircutPartId == null || skin == null || registry == null || skin.getHeadAccessory() == null) {
                return null;
            }

            PlayerSkinPart headAccessoryPart = registry.getHeadAccessories().get(skin.getHeadAccessory().getAssetId());
            if (headAccessoryPart == null) {
                return null;
            }

            String headAccessoryId = skin.getHeadAccessory().getAssetId();
            HeadAccessoryRule headAccessoryRule =
                    OfficialCharacterCreatorData.getHeadAccessoryRule(headAccessoryId);
            String headAccessoryType = headAccessoryRule != null && headAccessoryRule.headAccessoryType != null
                    ? headAccessoryRule.headAccessoryType
                    : String.valueOf(headAccessoryPart.getHeadAccessoryType());

            if (headAccessoryRule != null && headAccessoryRule.disablesHaircut) {
                debug("generic haircut disabled by head accessory"
                        + " | haircut=" + haircutPart.getId()
                        + " | headAccessory=" + headAccessoryId
                        + " | headAccessoryType=" + headAccessoryType);
                return null;
            }
            if ("Simple".equals(headAccessoryType)) {
                return null;
            }
            if (!"HalfCovering".equals(headAccessoryType)) {
                debug("generic haircut not used for head accessory type"
                        + " | haircut=" + haircutPart.getId()
                        + " | headAccessory=" + headAccessoryId
                        + " | headAccessoryType=" + headAccessoryType);
                return null;
            }
            if (!haircutPart.doesRequireGenericHaircut()) {
                debug("generic haircut not required"
                        + " | haircut=" + haircutPart.getId()
                        + " | headAccessory=" + headAccessoryPart.getId()
                        + " | headAccessoryType=" + headAccessoryType);
                return null;
            }

            PlayerSkinPart genericHaircutPart = findGenericHaircutPart(registry, haircutPart);
            if (genericHaircutPart == null) {
                debug("generic haircut missing"
                        + " | haircut=" + haircutPart.getId()
                        + " | hairType=" + haircutPart.getHairType()
                        + " | requiresGeneric=" + haircutPart.doesRequireGenericHaircut()
                        + " | headAccessory=" + headAccessoryPart.getId()
                        + " | headAccessoryType=" + headAccessoryType);
                return null;
            }

            PlayerSkin.PlayerSkinPartId genericHaircutPartId =
                    new PlayerSkin.PlayerSkinPartId(genericHaircutPart.getId(), haircutPartId.getTextureId(), null);
            ModelAttachment genericHaircutAttachment = createSkinAttachment(genericHaircutPart, genericHaircutPartId);
            debug("generic haircut resolved"
                    + " | originalHaircut=" + haircutPart.getId()
                    + " | genericHaircut=" + genericHaircutPart.getId()
                    + " | hairType=" + haircutPart.getHairType()
                    + " | headAccessory=" + headAccessoryPart.getId()
                    + " | headAccessoryType=" + headAccessoryType
                    + " | attachment=" + summarizeAttachment(genericHaircutAttachment));
            return genericHaircutAttachment;
        }

        private ModelAttachment resolveSimplifiedHaircutAttachment(PlayerSkinPart haircutPart,
                                                                  PlayerSkin.PlayerSkinPartId haircutPartId,
                                                                  PlayerSkin skin,
                                                                  CosmeticRegistry registry) {
            if (haircutPart == null || haircutPartId == null || skin == null || registry == null || skin.getHeadAccessory() == null) {
                return null;
            }

            PlayerSkinPart headAccessoryPart = registry.getHeadAccessories().get(skin.getHeadAccessory().getAssetId());
            if (headAccessoryPart == null) {
                return null;
            }

            String headAccessoryId = skin.getHeadAccessory().getAssetId();
            HeadAccessoryRule headAccessoryRule =
                    OfficialCharacterCreatorData.getHeadAccessoryRule(headAccessoryId);
            String headAccessoryType = headAccessoryRule != null && headAccessoryRule.headAccessoryType != null
                    ? headAccessoryRule.headAccessoryType
                    : String.valueOf(headAccessoryPart.getHeadAccessoryType());

            if (!"FullyCovering".equals(headAccessoryType)) {
                return null;
            }
            if (headAccessoryRule != null && headAccessoryRule.disablesHaircut) {
                return null;
            }
            if (!haircutPart.doesRequireGenericHaircut()) {
                return null;
            }

            ModelAttachment rebuiltAttachment = createSkinAttachment(haircutPart, haircutPartId);
            if (rebuiltAttachment == null) {
                return null;
            }

            ModelAttachment simplifiedAttachment = new ModelAttachment(
                    SIMPLIFIED_HAIRCUT_MODEL,
                    rebuiltAttachment.getTexture(),
                    rebuiltAttachment.getGradientSet(),
                    rebuiltAttachment.getGradientId(),
                    1.0
            );
            debug("simplified haircut resolved"
                    + " | originalHaircut=" + haircutPart.getId()
                    + " | simplifiedModel=" + SIMPLIFIED_HAIRCUT_MODEL
                    + " | headAccessory=" + headAccessoryPart.getId()
                    + " | headAccessoryType=" + headAccessoryType
                    + " | attachment=" + summarizeAttachment(simplifiedAttachment));
            return simplifiedAttachment;
        }

        private PlayerSkinPart findGenericHaircutPart(CosmeticRegistry registry, PlayerSkinPart haircutPart) {
            if (registry == null || registry.getHaircuts() == null || haircutPart == null) {
                return null;
            }

            String fallbackHaircutId = resolveHaircutFallbackId(haircutPart);
            if (fallbackHaircutId == null) {
                return null;
            }

            PlayerSkinPart fallbackHaircutPart = registry.getHaircuts().get(fallbackHaircutId);
            if (fallbackHaircutPart == null) {
                debug("generic haircut fallback id missing"
                        + " | haircut=" + haircutPart.getId()
                        + " | hairType=" + haircutPart.getHairType()
                        + " | fallbackHaircut=" + fallbackHaircutId);
            }
            return fallbackHaircutPart;
        }

        private String resolveHaircutFallbackId(PlayerSkinPart haircutPart) {
            if (haircutPart == null || haircutPart.getHairType() == null) {
                return null;
            }

            String hairType = String.valueOf(haircutPart.getHairType());
            String officialFallback = OfficialCharacterCreatorData.getHaircutFallbackId(hairType);
            if (officialFallback != null) {
                return officialFallback;
            }
            if ("Short".equals(hairType)) {
                return "GenericShort";
            }
            if ("Medium".equals(hairType)) {
                return "GenericMedium";
            }
            if ("Long".equals(hairType)) {
                return "GenericLong";
            }
            return null;
        }

        private ModelAttachment findMatchingBaseAttachment(ModelAttachment[] baseAttachments,
                                                           ModelAttachment rebuiltAttachment,
                                                           String attachmentModel) {
            if (baseAttachments == null || baseAttachments.length == 0) {
                return null;
            }

            if (rebuiltAttachment != null) {
                for (ModelAttachment baseAttachment : baseAttachments) {
                    if (attachmentsEquivalent(baseAttachment, rebuiltAttachment)) {
                        return baseAttachment;
                    }
                }
            }

            if (attachmentModel == null) {
                return null;
            }

            for (ModelAttachment baseAttachment : baseAttachments) {
                if (baseAttachment != null && stringEquals(baseAttachment.getModel(), attachmentModel)) {
                    return baseAttachment;
                }
            }
            return null;
        }

        private ModelAttachment findHaircutBaseAttachment(ModelAttachment[] baseAttachments) {
            if (baseAttachments == null || baseAttachments.length == 0) {
                return null;
            }

            for (ModelAttachment baseAttachment : baseAttachments) {
                if (baseAttachment != null && isHaircutAttachment(baseAttachment)) {
                    return baseAttachment;
                }
            }
            return null;
        }

        private String resolveSkinAttachmentModel(PlayerSkinPart part, PlayerSkin.PlayerSkinPartId partId) {
            if (part == null) {
                return null;
            }

            if (part.getVariants() != null && !part.getVariants().isEmpty() && partId.getVariantId() != null) {
                PlayerSkinPart.Variant variant = part.getVariants().get(partId.getVariantId());
                return variant == null ? null : variant.getModel();
            }

            return part.getModel();
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

        private boolean isAccessoryType(CosmeticType type) {
            return type == CosmeticType.HEAD_ACCESSORY
                    || type == CosmeticType.FACE_ACCESSORY
                    || type == CosmeticType.EAR_ACCESSORY;
        }

        private boolean isShieldCapAttachment(ModelAttachment attachment) {
            return attachment != null
                    && (BACK_ATTACHMENT_MODEL.equals(attachment.getModel())
                    || BACK_ATTACHMENT_CAPE_MODEL.equals(attachment.getModel()))
                    && (BACK_ATTACHMENT_TEXTURE.equals(attachment.getTexture())
                    || BACK_ATTACHMENT_VIBRANIUM_TEXTURE.equals(attachment.getTexture())
                    || BACK_ATTACHMENT_CARTER_TEXTURE.equals(attachment.getTexture())
                    || LEGACY_BACK_ATTACHMENT_TEXTURE.equals(attachment.getTexture()));
        }

        private boolean isHaircutAttachment(ModelAttachment attachment) {
            return attachment != null
                    && attachment.getModel() != null
                    && attachment.getModel().contains("Characters/Haircuts/");
        }

        private boolean hasShieldCapAttachment(Model model) {
            if (model == null || model.getAttachments() == null || model.getAttachments().length == 0) {
                return false;
            }

            for (ModelAttachment attachment : model.getAttachments()) {
                if (isShieldCapAttachment(attachment)) {
                    return true;
                }
            }
            return false;
        }

        private boolean stringEquals(String left, String right) {
            if (left == null) {
                return right == null;
            }
            return left.equals(right);
        }

        private boolean haveEquivalentAttachments(Model currentModel, Model appearanceModel) {
            ModelAttachment[] currentAttachments = currentModel.getAttachments();
            ModelAttachment[] appearanceAttachments = appearanceModel.getAttachments();

            int currentCount = currentAttachments == null ? 0 : currentAttachments.length;
            int appearanceCount = appearanceAttachments == null ? 0 : appearanceAttachments.length;
            if (currentCount != appearanceCount) {
                return false;
            }

            if (currentCount == 0) {
                return true;
            }

            for (ModelAttachment appearanceAttachment : appearanceAttachments) {
                if (!containsEquivalentAttachment(currentAttachments, appearanceAttachment)) {
                    return false;
                }
            }
            return true;
        }

        private boolean containsEquivalentAttachment(ModelAttachment[] attachments, ModelAttachment target) {
            if (attachments == null || target == null) {
                return false;
            }

            for (ModelAttachment candidate : attachments) {
                if (attachmentsEquivalent(candidate, target)) {
                    return true;
                }
            }
            return false;
        }

        private boolean areModelsVisuallyEquivalent(Model left, Model right) {
            if (left == null || right == null) {
                return false;
            }

            return stringEquals(left.getModelAssetId(), right.getModelAssetId())
                    && stringEquals(left.getModel(), right.getModel())
                    && stringEquals(left.getTexture(), right.getTexture())
                    && stringEquals(left.getGradientSet(), right.getGradientSet())
                    && stringEquals(left.getGradientId(), right.getGradientId())
                    && haveEquivalentAttachments(left, right);
        }

        private int countAttachments(ModelAttachment[] attachments) {
            if (attachments == null) {
                return 0;
            }

            int count = 0;
            for (ModelAttachment attachment : attachments) {
                if (attachment != null) {
                    count++;
                }
            }
            return count;
        }

        private boolean containsAttachmentModel(ModelAttachment[] attachments, String model) {
            if (attachments == null || model == null) {
                return false;
            }

            for (ModelAttachment attachment : attachments) {
                if (attachment != null && stringEquals(attachment.getModel(), model)) {
                    return true;
                }
            }
            return false;
        }

        private boolean attachmentsEquivalent(ModelAttachment left, ModelAttachment right) {
            return left != null
                    && right != null
                    && stringEquals(left.getModel(), right.getModel())
                    && stringEquals(left.getTexture(), right.getTexture())
                    && stringEquals(left.getGradientSet(), right.getGradientSet())
                    && stringEquals(left.getGradientId(), right.getGradientId());
        }

        private Model cloneModelWithAttachments(Model model, ModelAttachment[] attachments) {
            if (model == null) {
                return null;
            }

            try {
                Model copy = new Model(model);

                Field attachmentsField = Model.class.getDeclaredField("attachments");
                attachmentsField.setAccessible(true);
                attachmentsField.set(copy, attachments);

                Field cachedPacketField = Model.class.getDeclaredField("cachedPacket");
                cachedPacketField.setAccessible(true);
                cachedPacketField.set(copy, null);
                return copy;
            } catch (ReflectiveOperationException ignored) {
                return new Model(
                        model.getModelAssetId(),
                        model.getScale(),
                        model.getRandomAttachmentIds(),
                        attachments,
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
        }

        private Model setModelAttachmentsInPlace(Model model, ModelAttachment[] attachments) {
            if (model == null) {
                return null;
            }

            try {
                Field attachmentsField = Model.class.getDeclaredField("attachments");
                attachmentsField.setAccessible(true);
                attachmentsField.set(model, attachments);

                Field cachedPacketField = Model.class.getDeclaredField("cachedPacket");
                cachedPacketField.setAccessible(true);
                cachedPacketField.set(model, null);
                return model;
            } catch (ReflectiveOperationException ignored) {
                return cloneModelWithAttachments(model, attachments);
            }
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

        private void debug(String message) {
            if (DEBUG_VISUALS) {
                System.out.println(LOG_PREFIX + message);
            }
        }

        private String resolvePlayerDebugId(Player player) {
            if (player == null) {
                return "null";
            }

            try {
                if (player.getPlayerRef() != null) {
                    return String.valueOf(player.getPlayerRef().getUuid());
                }
            } catch (Throwable ignored) {
            }
            return "unknown";
        }

        private String summarizeSkinAccessories(PlayerSkinComponent playerSkinComponent) {
            if (playerSkinComponent == null || playerSkinComponent.getPlayerSkin() == null) {
                return "null";
            }

            com.hypixel.hytale.protocol.PlayerSkin skin = playerSkinComponent.getPlayerSkin();
            return "head=" + summarizeProtocolPartId(skin.headAccessory)
                    + ",face=" + summarizeProtocolPartId(skin.faceAccessory)
                    + ",ear=" + summarizeProtocolPartId(skin.earAccessory)
                    + ",cape=" + summarizeProtocolPartId(skin.cape);
        }

        private String summarizeSkinDetails(PlayerSkinComponent playerSkinComponent) {
            if (playerSkinComponent == null || playerSkinComponent.getPlayerSkin() == null) {
                return "null";
            }

            Object skin = playerSkinComponent.getPlayerSkin();
            return "modelAsset=" + readFieldValue(skin, "modelAsset")
                    + ",body=" + summarizeProtocolPartId(readFieldValue(skin, "body"))
                    + ",haircut=" + summarizeProtocolPartId(readFieldValue(skin, "haircut"))
                    + ",face=" + summarizeProtocolPartId(readFieldValue(skin, "face"))
                    + ",eyes=" + summarizeProtocolPartId(readFieldValue(skin, "eyes"))
                    + ",ears=" + summarizeProtocolPartId(readFieldValue(skin, "ears"))
                    + ",mouth=" + summarizeProtocolPartId(readFieldValue(skin, "mouth"))
                    + ",headAccessory=" + summarizeProtocolPartId(readFieldValue(skin, "headAccessory"))
                    + ",faceAccessory=" + summarizeProtocolPartId(readFieldValue(skin, "faceAccessory"))
                    + ",earAccessory=" + summarizeProtocolPartId(readFieldValue(skin, "earAccessory"))
                    + ",cape=" + summarizeProtocolPartId(readFieldValue(skin, "cape"));
        }

        private String summarizeProtocolPartId(Object partId) {
            if (partId == null) {
                return "null";
            }
            return readFieldValue(partId, "assetId")
                    + "/" + readFieldValue(partId, "textureId")
                    + "/" + readFieldValue(partId, "variantId");
        }

        private String summarizePartId(PlayerSkin.PlayerSkinPartId partId) {
            if (partId == null) {
                return "null";
            }
            return partId.getAssetId() + "/" + partId.getTextureId() + "/" + partId.getVariantId();
        }

        private String summarizeModel(Model model) {
            if (model == null) {
                return "null";
            }
            return "assetId=" + model.getModelAssetId()
                    + ",model=" + model.getModel()
                    + ",texture=" + model.getTexture()
                    + ",gradient=" + model.getGradientSet() + "/" + model.getGradientId()
                    + ",attachments=" + summarizeAttachments(model.getAttachments());
        }

        private String summarizeAttachments(ModelAttachment[] attachments) {
            if (attachments == null || attachments.length == 0) {
                return "[]";
            }

            List<String> values = new ArrayList<>();
            for (ModelAttachment attachment : attachments) {
                values.add(summarizeAttachment(attachment));
            }
            return values.toString();
        }

        private String summarizeAttachments(List<ModelAttachment> attachments) {
            if (attachments == null || attachments.isEmpty()) {
                return "[]";
            }

            List<String> values = new ArrayList<>();
            for (ModelAttachment attachment : attachments) {
                values.add(summarizeAttachment(attachment));
            }
            return values.toString();
        }

        private String summarizeAttachment(ModelAttachment attachment) {
            if (attachment == null) {
                return "null";
            }
            return attachment.getModel()
                    + "|" + attachment.getTexture()
                    + "|" + attachment.getGradientSet()
                    + "|" + attachment.getGradientId();
        }

        private String summarizeKeys(java.util.Map<String, ?> values) {
            if (values == null || values.isEmpty()) {
                return "[]";
            }
            return values.keySet().toString();
        }

        private Object readFieldValue(Object target, String fieldName) {
            if (target == null) {
                return null;
            }

            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                return "?";
            }
        }
    }

    private static final class OfficialCharacterCreatorData {
        private static final String HAIRCUT_FALLBACKS_ENTRY = "Cosmetics/CharacterCreator/HaircutFallbacks.json";
        private static final String HEAD_ACCESSORIES_ENTRY = "Cosmetics/CharacterCreator/HeadAccessory.json";
        private static volatile boolean loaded;
        private static Map<String, String> haircutFallbacks = Map.of();
        private static Map<String, HeadAccessoryRule> headAccessoryRules = Map.of();

        private OfficialCharacterCreatorData() {
        }

        @Nullable
        private static String getHaircutFallbackId(@Nullable String hairType) {
            ensureLoaded();
            if (hairType == null) {
                return null;
            }
            return haircutFallbacks.get(hairType);
        }

        @Nullable
        private static HeadAccessoryRule getHeadAccessoryRule(@Nullable String headAccessoryId) {
            ensureLoaded();
            if (headAccessoryId == null) {
                return null;
            }
            return headAccessoryRules.get(headAccessoryId);
        }

        private static void ensureLoaded() {
            if (loaded) {
                return;
            }
            synchronized (OfficialCharacterCreatorData.class) {
                if (loaded) {
                    return;
                }
                load();
                loaded = true;
            }
        }

        private static void load() {
            Path assetsZip = resolveAssetsZipPath();
            if (assetsZip == null) {
                debugStatic("official character creator data unavailable | assetsZip=missing");
                haircutFallbacks = Map.of();
                headAccessoryRules = Map.of();
                return;
            }

            Map<String, String> loadedHaircutFallbacks = new HashMap<>();
            Map<String, HeadAccessoryRule> loadedHeadAccessoryRules = new HashMap<>();
            try (ZipFile zipFile = new ZipFile(assetsZip.toFile())) {
                readHaircutFallbacks(zipFile, loadedHaircutFallbacks);
                readHeadAccessoryRules(zipFile, loadedHeadAccessoryRules);
                haircutFallbacks = Map.copyOf(loadedHaircutFallbacks);
                headAccessoryRules = Map.copyOf(loadedHeadAccessoryRules);
                debugStatic("official character creator data loaded"
                        + " | assetsZip=" + assetsZip
                        + " | haircutFallbacks=" + haircutFallbacks
                        + " | headAccessoryRules=" + headAccessoryRules.size());
            } catch (Throwable throwable) {
                debugStatic("official character creator data load failed"
                        + " | assetsZip=" + assetsZip
                        + " | reason=" + throwable.getClass().getSimpleName()
                        + ": " + throwable.getMessage());
                haircutFallbacks = Map.of();
                headAccessoryRules = Map.of();
            }
        }

        @Nullable
        private static Path resolveAssetsZipPath() {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                Path candidate = Paths.get(appData, "Hytale", "install", "pre-release", "package", "game", "latest", "Assets.zip");
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }

            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                Path candidate = Paths.get(userHome, "AppData", "Roaming", "Hytale", "install", "pre-release", "package", "game", "latest", "Assets.zip");
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private static void readHaircutFallbacks(ZipFile zipFile, Map<String, String> target) throws Exception {
            JsonObject root = readJsonObject(zipFile, HAIRCUT_FALLBACKS_ENTRY);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    target.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        private static void readHeadAccessoryRules(ZipFile zipFile, Map<String, HeadAccessoryRule> target) throws Exception {
            JsonElement root = readJsonElement(zipFile, HEAD_ACCESSORIES_ENTRY);
            if (root == null || !root.isJsonArray()) {
                return;
            }

            for (JsonElement element : root.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                JsonElement idElement = object.get("Id");
                if (idElement == null || !idElement.isJsonPrimitive()) {
                    continue;
                }

                String id = idElement.getAsString();
                String headAccessoryType = getOptionalString(object, "HeadAccessoryType");
                boolean disablesHaircut = "Haircut".equals(getOptionalString(object, "DisableCharacterPartCategory"));
                target.put(id, new HeadAccessoryRule(headAccessoryType, disablesHaircut));
            }
        }

        @Nullable
        private static JsonObject readJsonObject(ZipFile zipFile, String entryName) throws Exception {
            JsonElement element = readJsonElement(zipFile, entryName);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }

        @Nullable
        private static JsonElement readJsonElement(ZipFile zipFile, String entryName) throws Exception {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }

        @Nullable
        private static String getOptionalString(JsonObject object, String key) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) {
                return null;
            }
            return element.getAsString();
        }
    }

    private static void debugStatic(String message) {
        if (DEBUG_VISUALS) {
            System.out.println(LOG_PREFIX + message);
        }
    }

    private static final class HeadAccessoryRule {
        private final String headAccessoryType;
        private final boolean disablesHaircut;

        private HeadAccessoryRule(@Nullable String headAccessoryType, boolean disablesHaircut) {
            this.headAccessoryType = headAccessoryType;
            this.disablesHaircut = disablesHaircut;
        }
    }
}
