package co.carrd.starkymods.visuals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.player.ReticleEvent;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemReticleConfig;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class ShieldCapReturnReticleInjector {
    private static final String RETURN_WINDOW_EVENT_TAG = "ShieldCap_ReturnWindow";
    private static final String RETURN_WINDOW_RETICLE_PART = "UI/Reticles/Melee.png";
    private static final float RETURN_WINDOW_DURATION_SECONDS = 0.14f;

    private static Field serverEventsField;
    private static Field indexedServerEventsField;
    private static Field cachedPacketField;
    private static Constructor<?> reticleWithDurationCtor;

    private static ReticleEvent returnWindowReticleEvent;

    static {
        try {
            serverEventsField = ItemReticleConfig.class.getDeclaredField("serverEvents");
            serverEventsField.setAccessible(true);

            indexedServerEventsField = ItemReticleConfig.class.getDeclaredField("indexedServerEvents");
            indexedServerEventsField.setAccessible(true);

            cachedPacketField = ItemReticleConfig.class.getDeclaredField("cachedPacket");
            cachedPacketField.setAccessible(true);

            Class<?> reticleWithDurationClass = Class.forName(
                    "com.hypixel.hytale.server.core.asset.type.item.config.ItemReticleConfig$ItemReticleWithDuration");
            reticleWithDurationCtor = reticleWithDurationClass.getDeclaredConstructor(boolean.class, String[].class, float.class);
            reticleWithDurationCtor.setAccessible(true);
        } catch (Exception ignored) {
            serverEventsField = null;
            indexedServerEventsField = null;
            cachedPacketField = null;
            reticleWithDurationCtor = null;
        }
    }

    private EventRegistration<Class<ItemReticleConfig>, LoadedAssetsEvent<String, ItemReticleConfig, IndexedLookupTableAssetMap<String, ItemReticleConfig>>> reticleRegistration;

    public void register(JavaPlugin plugin) {
        reticleRegistration = plugin.getEventRegistry().register(
                LoadedAssetsEvent.class,
                ItemReticleConfig.class,
                this::onReticlesLoaded
        );
    }

    public void shutdown() {
        if (reticleRegistration != null) {
            reticleRegistration.unregister();
            reticleRegistration = null;
        }
    }

    public static void sendReturnWindowReticle(PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid() || returnWindowReticleEvent == null) {
            return;
        }
        playerRef.getPacketHandler().writeNoCache(returnWindowReticleEvent);
    }

    public static long getReturnWindowDurationMs() {
        return Math.round(RETURN_WINDOW_DURATION_SECONDS * 1000.0f);
    }

    @SuppressWarnings("unchecked")
    private void onReticlesLoaded(LoadedAssetsEvent<String, ItemReticleConfig, IndexedLookupTableAssetMap<String, ItemReticleConfig>> event) {
        if (event == null
                || serverEventsField == null
                || indexedServerEventsField == null
                || cachedPacketField == null
                || reticleWithDurationCtor == null) {
            return;
        }

        int returnWindowTagIndex = AssetRegistry.getOrCreateTagIndex(RETURN_WINDOW_EVENT_TAG);
        returnWindowReticleEvent = new ReticleEvent(returnWindowTagIndex);

        for (ItemReticleConfig config : event.getLoadedAssets().values()) {
            injectIntoReticleConfig(config, returnWindowTagIndex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoReticleConfig(ItemReticleConfig config, int returnWindowTagIndex) {
        if (config == null) {
            return;
        }

        try {
            Object returnWindowReticle = reticleWithDurationCtor.newInstance(
                    false,
                    new String[]{RETURN_WINDOW_RETICLE_PART},
                    RETURN_WINDOW_DURATION_SECONDS
            );

            Map<String, Object> serverEvents = (Map<String, Object>) serverEventsField.get(config);
            if (serverEvents == null) {
                serverEvents = new HashMap<>();
            } else {
                serverEvents = new HashMap<>(serverEvents);
            }
            serverEvents.put(RETURN_WINDOW_EVENT_TAG, returnWindowReticle);
            serverEventsField.set(config, serverEvents);

            Int2ObjectMap<Object> indexedEvents = (Int2ObjectMap<Object>) indexedServerEventsField.get(config);
            if (indexedEvents == null) {
                indexedEvents = new Int2ObjectOpenHashMap<>();
            } else {
                indexedEvents = new Int2ObjectOpenHashMap<>(indexedEvents);
            }
            indexedEvents.put(returnWindowTagIndex, returnWindowReticle);
            indexedServerEventsField.set(config, indexedEvents);

            cachedPacketField.set(config, null);
        } catch (Exception ignored) {
        }
    }
}
