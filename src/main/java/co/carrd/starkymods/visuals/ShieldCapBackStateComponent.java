package co.carrd.starkymods.visuals;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Objects;

public final class ShieldCapBackStateComponent implements Component<EntityStore> {
    public static final BuilderCodec<ShieldCapBackStateComponent> CODEC =
            BuilderCodec.builder(ShieldCapBackStateComponent.class, ShieldCapBackStateComponent::new)
                    .append(
                            new KeyedCodec<>("ShowBackShield", Codec.BOOLEAN),
                            (component, value) -> component.showBackShield = value,
                            component -> component.showBackShield
                    )
                    .add()
                    .build();

    private boolean showBackShield;
    private boolean vibraniumBackShield;
    private boolean dirty;
    private boolean pendingApply;
    private boolean pendingModelReset;
    private boolean awaitingNaturalModel;
    private Model pendingBaseModel;
    private Model naturalBaseModel;
    private ModelAttachment[] preservedExtraAttachments;

    public boolean shouldShowBackShield() {
        return showBackShield;
    }

    public boolean updateShowBackShield(boolean value) {
        if (showBackShield == value) {
            return false;
        }

        showBackShield = value;
        dirty = true;
        return true;
    }

    public boolean shouldUseVibraniumBackShield() {
        return vibraniumBackShield;
    }

    public boolean updateVibraniumBackShield(boolean value) {
        if (vibraniumBackShield == value) {
            return false;
        }

        vibraniumBackShield = value;
        dirty = true;
        return true;
    }

    public void rebuild() {
        dirty = true;
    }

    public boolean consumeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    public boolean isPendingApply() {
        return pendingApply;
    }

    public void setPendingApply(boolean pendingApply) {
        this.pendingApply = pendingApply;
    }

    public boolean isPendingModelReset() {
        return pendingModelReset;
    }

    public void setPendingModelReset(boolean pendingModelReset) {
        this.pendingModelReset = pendingModelReset;
    }

    public boolean isAwaitingNaturalModel() {
        return awaitingNaturalModel;
    }

    public void setAwaitingNaturalModel(boolean awaitingNaturalModel) {
        this.awaitingNaturalModel = awaitingNaturalModel;
    }

    public void setPendingBaseModel(Model pendingBaseModel) {
        this.pendingBaseModel = pendingBaseModel;
    }

    public Model getPendingBaseModel() {
        return pendingBaseModel;
    }

    public void clearPendingBaseModel() {
        pendingBaseModel = null;
    }

    public void setNaturalBaseModel(Model naturalBaseModel) {
        this.naturalBaseModel = naturalBaseModel;
    }

    public Model getNaturalBaseModel() {
        return naturalBaseModel;
    }

    public void clearNaturalBaseModel() {
        naturalBaseModel = null;
    }

    public void setPreservedExtraAttachments(ModelAttachment[] preservedExtraAttachments) {
        this.preservedExtraAttachments =
                preservedExtraAttachments == null ? null : preservedExtraAttachments.clone();
    }

    public ModelAttachment[] getPreservedExtraAttachments() {
        return preservedExtraAttachments == null ? null : preservedExtraAttachments.clone();
    }

    public void clearPreservedExtraAttachments() {
        preservedExtraAttachments = null;
    }

    @Override
    public ShieldCapBackStateComponent clone() {
        ShieldCapBackStateComponent copy = new ShieldCapBackStateComponent();
        copy.showBackShield = showBackShield;
        copy.vibraniumBackShield = vibraniumBackShield;
        copy.dirty = dirty;
        copy.pendingApply = pendingApply;
        copy.pendingModelReset = pendingModelReset;
        copy.awaitingNaturalModel = awaitingNaturalModel;
        copy.pendingBaseModel = pendingBaseModel;
        copy.naturalBaseModel = naturalBaseModel;
        copy.preservedExtraAttachments =
                preservedExtraAttachments == null ? null : preservedExtraAttachments.clone();
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ShieldCapBackStateComponent that)) {
            return false;
        }

        return showBackShield == that.showBackShield
                && vibraniumBackShield == that.vibraniumBackShield;
    }

    @Override
    public int hashCode() {
        return Objects.hash(showBackShield, vibraniumBackShield);
    }

    public static ComponentType<EntityStore, ShieldCapBackStateComponent> getComponentType() {
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("ShieldCap plugin is not available");
        }

        return plugin.getShieldCapBackStateComponentType();
    }
}
