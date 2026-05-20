package co.carrd.starkymods.damage;

import co.carrd.starkymods.config.ShieldCapCraft;
import co.carrd.starkymods.config.ShieldCapCraftConfigManager;
import co.carrd.starkymods.config.ShieldCapConfigPaths;
import co.carrd.starkymods.config.ShieldCapDamageConfigManager;
import co.carrd.starkymods.config.ShieldCapDamages;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ShieldCapDamageAssetGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<ItemAsset> ITEM_ASSETS = List.of(
            new ItemAsset("Weapon_Shield_CaptainAmerica_Starky", "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainAmerica_Starky.json"),
            new ItemAsset("Weapon_Shield_AntiCaptainAmerica_Starky", "Server/Item/Items/Weapon/Shield/Weapon_Shield_AntiCaptainAmerica_Starky.json"),
            new ItemAsset("Weapon_Shield_CaptainCarter_Starky", "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainCarter_Starky.json"),
            new ItemAsset("Weapon_Shield_Georgio_Starky", "Server/Item/Items/Weapon/Shield/Weapon_Shield_Georgio_Starky.json"),
            new ItemAsset("Weapon_Shield_Vibranium_Starky", "Server/Item/Items/Weapon/Shield/Weapon_Shield_Vibranium_Starky.json"),
            new ItemAsset("Weapon_ShieldLeft_CaptainAmerica_Starky", "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainAmerica_Starky.json"),
            new ItemAsset("Weapon_ShieldLeft_AntiCaptainAmerica_Starky", "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_AntiCaptainAmerica_Starky.json"),
            new ItemAsset("Weapon_ShieldLeft_CaptainCarter_Starky", "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainCarter_Starky.json"),
            new ItemAsset("Weapon_ShieldLeft_Georgio_Starky", "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_Georgio_Starky.json"),
            new ItemAsset("Weapon_ShieldLeft_Vibranium_Starky", "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_Vibranium_Starky.json"),
            new ItemAsset("Weapon_ShieldCap_Thrown_Starky", "Server/Item/Items/Weapon_ShieldCap_Thrown_Starky.json")
    );

    private ShieldCapDamageAssetGenerator() {
    }

    public static boolean generateAndReload() {
        try {
            ShieldCapDamages config = ShieldCapDamageConfigManager.getConfig();
            if (config == null) {
                return false;
            }

            Path outputFolder = ShieldCapConfigPaths.getGeneratedFolder().toPath();
            File outputDir = outputFolder.toFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            Integer durability = null;
            ShieldCapCraft craft = ShieldCapCraftConfigManager.getConfig();
            if (craft != null && craft.weaponMaxDurability != null) {
                durability = Math.max(0, craft.weaponMaxDurability);
            }

            List<RawAsset<String>> itemAssets = new ArrayList<>();
            for (ItemAsset itemAsset : ITEM_ASSETS) {
                JsonObject itemJson = loadJson(itemAsset.resourcePath());
                if (itemJson == null) {
                    continue;
                }
                if (durability != null) {
                    itemJson.addProperty("MaxDurability", durability);
                }
                if (!"Weapon_ShieldCap_Thrown_Starky".equals(itemAsset.id())) {
                    applyConfig(itemJson, config);
                }
                File output = outputFolder.resolve(itemAsset.id() + ".json").toFile();
                try (FileWriter writer = new FileWriter(output)) {
                    GSON.toJson(itemJson, writer);
                }
                itemAssets.add(new RawAsset<>(itemAsset.id(), output.toPath()));
            }

            if (itemAssets.isEmpty()) {
                return false;
            }

            Item.getAssetStore().loadBuffersWithKeys(
                    "StarkyShieldCap_DynamicDamages",
                    itemAssets,
                    AssetUpdateQuery.DEFAULT,
                    false
            );

            List<RawAsset<String>> interactionAssets = generateInteractionAssets(config, outputFolder);
            if (!interactionAssets.isEmpty()) {
                Interaction.getAssetStore().loadBuffersWithKeys(
                        "StarkyShieldCap_DynamicDamages",
                        interactionAssets,
                        AssetUpdateQuery.DEFAULT,
                        false
                );
            }
            System.out.println("[ShieldCap] Dynamic damage, knockback, force and durability overrides applied.");
            return true;
        } catch (Exception e) {
            System.out.println("[ShieldCap] Failed to apply dynamic damage assets:");
            e.printStackTrace();
            return false;
        }
    }

    private static List<RawAsset<String>> generateInteractionAssets(ShieldCapDamages config, Path outputFolder) throws Exception {
        List<RawAsset<String>> assets = new ArrayList<>();
        for (ShieldCapDamageConfigManager.DamageDefinition definition : ShieldCapDamageConfigManager.getAllDefinitions()) {
            if (definition.assetId() == null || definition.assetPath() == null) {
                continue;
            }
            JsonObject interactionJson = loadJson(definition.assetPath());
            if (interactionJson == null) {
                continue;
            }
            double value = ShieldCapDamageConfigManager.getLaunchForceDefinitions().contains(definition)
                    ? config.getLaunchForce(definition.displayKey())
                    : config.getDamage(definition.displayKey());
            applyExternalValue(interactionJson, definition.type(), value);
            File output = outputFolder.resolve(definition.assetId() + ".json").toFile();
            try (FileWriter writer = new FileWriter(output)) {
                GSON.toJson(interactionJson, writer);
            }
            assets.add(new RawAsset<>(definition.assetId(), output.toPath()));
        }
        return assets;
    }

    private static void applyConfig(JsonObject itemJson, ShieldCapDamages config) {
        JsonObject interactionVars = itemJson.getAsJsonObject("InteractionVars");
        if (interactionVars == null) {
            return;
        }
        for (ShieldCapDamageConfigManager.DamageDefinition definition : ShieldCapDamageConfigManager.getDamageDefinitions()) {
            if (definition.assetId() != null || definition.type() == ShieldCapDamageConfigManager.ValueType.PLACEHOLDER) {
                continue;
            }
            double value = config.getDamage(definition.displayKey());
            for (String interactionVar : definition.interactionVars()) {
                applyValue(interactionVars, interactionVar, definition.type(), value);
            }
        }
        for (ShieldCapDamageConfigManager.DamageDefinition definition : ShieldCapDamageConfigManager.getLaunchForceDefinitions()) {
            if (definition.assetId() != null || definition.type() == ShieldCapDamageConfigManager.ValueType.PLACEHOLDER) {
                continue;
            }
            double value = config.getLaunchForce(definition.displayKey());
            for (String interactionVar : definition.interactionVars()) {
                applyValue(interactionVars, interactionVar, definition.type(), value);
            }
        }
    }

    private static void applyExternalValue(JsonObject interactionJson,
                                           ShieldCapDamageConfigManager.ValueType type,
                                           double value) {
        if (interactionJson == null) {
            return;
        }
        if (type == ShieldCapDamageConfigManager.ValueType.PROJECTILE_DAMAGE) {
            JsonObject damageCalculator = interactionJson.getAsJsonObject("DamageCalculator");
            JsonObject baseDamage = damageCalculator == null ? null : damageCalculator.getAsJsonObject("BaseDamage");
            if (baseDamage != null) {
                baseDamage.addProperty("Projectile", Math.max(0.0, value));
            }
            return;
        }
        if (type == ShieldCapDamageConfigManager.ValueType.APPLY_FORCE) {
            applyFirstApplyForce(interactionJson, value);
        }
    }

    private static boolean applyFirstApplyForce(com.google.gson.JsonElement element, double value) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("Type") && "ApplyForce".equals(object.get("Type").getAsString())) {
                object.addProperty("Force", Math.max(0.0, value));
                return true;
            }
            for (String key : object.keySet()) {
                if (applyFirstApplyForce(object.get(key), value)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonArray()) {
            for (com.google.gson.JsonElement child : element.getAsJsonArray()) {
                if (applyFirstApplyForce(child, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyValue(JsonObject interactionVars, String varKey,
                                   ShieldCapDamageConfigManager.ValueType type, double value) {
        if (interactionVars == null || varKey == null || !interactionVars.has(varKey)) {
            return;
        }
        JsonObject var = interactionVars.getAsJsonObject(varKey);
        JsonArray interactions = var == null ? null : var.getAsJsonArray("Interactions");
        if (interactions == null || interactions.isEmpty() || !interactions.get(0).isJsonObject()) {
            return;
        }
        JsonObject interaction = interactions.get(0).getAsJsonObject();
        if (type == ShieldCapDamageConfigManager.ValueType.DAMAGE) {
            JsonObject damageCalculator = interaction.getAsJsonObject("DamageCalculator");
            JsonObject baseDamage = damageCalculator == null ? null : damageCalculator.getAsJsonObject("BaseDamage");
            if (baseDamage != null) {
                baseDamage.addProperty("Physical", Math.max(0.0, value));
            }
            return;
        }
        if (type == ShieldCapDamageConfigManager.ValueType.KNOCKBACK) {
            JsonObject damageEffects = interaction.getAsJsonObject("DamageEffects");
            JsonObject knockback = damageEffects == null ? null : damageEffects.getAsJsonObject("Knockback");
            if (knockback != null) {
                knockback.addProperty("Force", Math.max(0.0, value));
            }
        }
    }

    private static JsonObject loadJson(String path) {
        try (InputStream stream = ShieldCapDamageAssetGenerator.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ItemAsset(String id, String resourcePath) {
    }
}
