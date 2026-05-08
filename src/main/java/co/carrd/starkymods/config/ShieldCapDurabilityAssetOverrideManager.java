package co.carrd.starkymods.config;

import co.carrd.starkymods.damage.ShieldCapDamageAssetGenerator;

public final class ShieldCapDurabilityAssetOverrideManager {
    private ShieldCapDurabilityAssetOverrideManager() {
    }

    public static boolean applyConfiguredDurabilityAssets() {
        return ShieldCapDamageAssetGenerator.generateAndReload();
    }
}
