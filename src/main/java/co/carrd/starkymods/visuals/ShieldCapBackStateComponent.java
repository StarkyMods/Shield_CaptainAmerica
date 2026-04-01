package co.carrd.starkymods.visuals;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
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
    private boolean dirty;
    private boolean pendingApply;

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

    @Override
    public ShieldCapBackStateComponent clone() {
        ShieldCapBackStateComponent copy = new ShieldCapBackStateComponent();
        copy.showBackShield = showBackShield;
        copy.dirty = dirty;
        copy.pendingApply = pendingApply;
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ShieldCapBackStateComponent that)) {
            return false;
        }

        return showBackShield == that.showBackShield;
    }

    @Override
    public int hashCode() {
        return Objects.hash(showBackShield);
    }

    public static ComponentType<EntityStore, ShieldCapBackStateComponent> getComponentType() {
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("ShieldCap plugin is not available");
        }

        return plugin.getShieldCapBackStateComponentType();
    }
}
