package co.carrd.starkymods.compat;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.plugin.PluginManager;

public final class ShieldCapOptionalModSupport {
    private static final PluginIdentifier CAPTAIN_AMERICA_SUIT_ID =
            new PluginIdentifier("Kleinstadtfrettchen", "Captain America Suit");
    private static final String CAPTAIN_AMERICA_SUIT_RECIPE_ID = "AntiCapSuit_Chest_Craft";

    private ShieldCapOptionalModSupport() {
    }

    public static boolean isCaptainAmericaSuitActive() {
        PluginManager pluginManager = PluginManager.get();
        if (pluginManager != null && pluginManager.getPlugin(CAPTAIN_AMERICA_SUIT_ID) != null) {
            return true;
        }

        return isCaptainAmericaSuitAssetPackLoaded();
    }

    private static boolean isCaptainAmericaSuitAssetPackLoaded() {
        try {
            return CraftingRecipe.getAssetMap().getAsset(CAPTAIN_AMERICA_SUIT_RECIPE_ID) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
