package co.carrd.starkymods.interactions;

import co.carrd.starkymods.config.ShieldCapConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCapSignatureEnergyStore {
    private static final String STORE_NAMESPACE = "co.carrd.starkymods.shieldcap.signature_energy";
    private static final Map<String, Float> CACHE = new ConcurrentHashMap<>();

    private ShieldCapSignatureEnergyStore() {
    }

    public static void save(UUID playerUuid, float value) {
        if (!ShieldCapConfigManager.isSignatureEnergyKeepOnSwapEnabled()) {
            return;
        }
        String key = getKey(playerUuid);
        if (key == null) {
            return;
        }
        CACHE.put(key, value);
    }

    public static Float load(UUID playerUuid) {
        if (!ShieldCapConfigManager.isSignatureEnergyKeepOnSwapEnabled()) {
            return null;
        }
        String key = getKey(playerUuid);
        if (key == null) {
            return null;
        }
        return CACHE.get(key);
    }

    private static String getKey(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return STORE_NAMESPACE + ":" + playerUuid;
    }
}
