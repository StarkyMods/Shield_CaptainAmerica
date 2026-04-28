package co.carrd.starkymods.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ShieldCapDurabilityAssetOverrideManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PRIMARY_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String LEFT_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String VIBRANIUM_PRIMARY_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String CARTER_PRIMARY_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String CARTER_LEFT_ID = "Weapon_ShieldLeft_CaptainCarter_Starky";
    private static final String THROWN_ID = "Weapon_ShieldCap_Thrown_Starky";
    private static final String PRIMARY_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainAmerica_Starky.json";
    private static final String LEFT_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainAmerica_Starky.json";
    private static final String VIBRANIUM_PRIMARY_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_Vibranium_Starky.json";
    private static final String VIBRANIUM_LEFT_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_Vibranium_Starky.json";
    private static final String CARTER_PRIMARY_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainCarter_Starky.json";
    private static final String CARTER_LEFT_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainCarter_Starky.json";
    private static final String THROWN_ASSET_PATH =
            "Server/Item/Items/Weapon_ShieldCap_Thrown_Starky.json";

    private ShieldCapDurabilityAssetOverrideManager() {
    }

    public static boolean applyConfiguredDurabilityAssets() {
        ShieldCapCraft craftConfig = ShieldCapCraftConfigManager.getConfig();
        if (craftConfig == null || craftConfig.weaponMaxDurability == null) {
            return false;
        }

        int maxDurability = Math.max(0, craftConfig.weaponMaxDurability);
        try {
            File outputFolder = ShieldCapConfigPaths.getGeneratedFolder();
            if (!outputFolder.exists()) {
                outputFolder.mkdirs();
            }

            List<RawAsset<String>> itemAssets = new ArrayList<>();
            addItemOverride(itemAssets, PRIMARY_ID, PRIMARY_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, LEFT_ID, LEFT_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, VIBRANIUM_PRIMARY_ID, VIBRANIUM_PRIMARY_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, VIBRANIUM_LEFT_ID, VIBRANIUM_LEFT_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, CARTER_PRIMARY_ID, CARTER_PRIMARY_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, CARTER_LEFT_ID, CARTER_LEFT_ASSET_PATH, maxDurability, outputFolder.toPath());
            addItemOverride(itemAssets, THROWN_ID, THROWN_ASSET_PATH, maxDurability, outputFolder.toPath());

            if (itemAssets.isEmpty()) {
                return false;
            }

            Item.getAssetStore().loadBuffersWithKeys(
                    "StarkyShieldCap_CraftOverrides",
                    itemAssets,
                    AssetUpdateQuery.DEFAULT,
                    false
            );
            System.out.println("[ShieldCap] MaxDurability override applied for ShieldCap items.");
            return true;
        } catch (Exception e) {
            System.out.println("[ShieldCap] Failed to apply ShieldCap item durability overrides:");
            e.printStackTrace();
            return false;
        }
    }

    private static void addItemOverride(List<RawAsset<String>> itemAssets,
                                        String itemId,
                                        String resourcePath,
                                        int maxDurability,
                                        Path outputFolder) throws Exception {
        JsonObject itemJson = readJson(resourcePath);
        if (itemJson == null) {
            return;
        }

        itemJson.addProperty("MaxDurability", maxDurability);
        File output = outputFolder.resolve(itemId + ".json").toFile();
        try (FileWriter writer = new FileWriter(output)) {
            GSON.toJson(itemJson, writer);
        }
        itemAssets.add(new RawAsset<>(itemId, output.toPath()));
    }

    private static JsonObject readJson(String resourcePath) throws Exception {
        try (InputStream stream = ShieldCapDurabilityAssetOverrideManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        }
    }
}
