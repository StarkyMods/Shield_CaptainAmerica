package co.carrd.starkymods.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public final class ShieldCapConfigManager {
    private static final File FOLDER = ShieldCapConfigPaths.getFolder();
    private static final File MAIN_CONFIG_FILE = new File(FOLDER, "shieldcapconfig.json");
    private static final String SHIELD_RECIPE_ID = "ShieldCap_Craft";
    private static final String VIBRANIUM_SHIELD_RECIPE_ID = "ShieldCap_Vibranium_Craft";
    private static final List<String> SHIELD_RECIPE_IDS = List.of(SHIELD_RECIPE_ID, VIBRANIUM_SHIELD_RECIPE_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ShieldCapConfig mainConfig;
    private static volatile long mainConfigLastKnownModified = Long.MIN_VALUE;

    private ShieldCapConfigManager() {
    }

    public static void init() {
        try {
            if (!FOLDER.exists()) {
                FOLDER.mkdirs();
            }

            if (!MAIN_CONFIG_FILE.exists()) {
                mainConfig = createDefaultMainConfig();
                saveMainConfig();
                return;
            }

            loadMainConfig();
        } catch (Exception e) {
            System.out.println("[ShieldCap] Error initializing shieldcapconfig.json:");
            e.printStackTrace();
        }
    }

    public static File getMainConfigFile() {
        return MAIN_CONFIG_FILE;
    }

    public static String getShieldRecipeId() {
        return SHIELD_RECIPE_ID;
    }

    public static boolean isShieldRecipeId(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return false;
        }
        return SHIELD_RECIPE_IDS.stream().anyMatch(id -> id.equalsIgnoreCase(recipeId));
    }

    public static boolean isCraftingAllowed() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        return mainConfig == null || !Boolean.FALSE.equals(mainConfig.allowShieldCraft);
    }

    public static boolean isSignatureEnergyKeepOnSwapEnabled() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        return mainConfig == null || !Boolean.FALSE.equals(mainConfig.shieldKeepsSignatureEnergyWhenSwapped);
    }

    public static boolean isBackShieldVisualEnabled() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        return mainConfig == null || !Boolean.FALSE.equals(mainConfig.visualShieldInPlayersBack);
    }

    public static void saveCraftState(boolean allowed) {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        if (mainConfig == null) {
            mainConfig = createDefaultMainConfig();
        }
        mainConfig.allowShieldCraft = allowed;
        saveMainConfig();
    }

    public static boolean reloadMainConfigIfValid() {
        try {
            ShieldCapConfig parsed = parseMainConfigFile();
            boolean changed = normalizeMainConfig(parsed);
            mainConfig = parsed;
            if (changed) {
                saveMainConfig();
            } else {
                updateMainConfigLastKnownModified();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ShieldCapConfig createDefaultMainConfig() {
        ShieldCapConfig config = new ShieldCapConfig();
        config.allowShieldCraft = true;
        config.shieldKeepsSignatureEnergyWhenSwapped = true;
        config.visualShieldInPlayersBack = true;
        return config;
    }

    private static void loadMainConfig() {
        try (FileReader reader = new FileReader(MAIN_CONFIG_FILE)) {
            mainConfig = GSON.fromJson(reader, ShieldCapConfig.class);
            if (mainConfig == null) {
                mainConfig = createDefaultMainConfig();
            }
            if (normalizeMainConfig(mainConfig)) {
                saveMainConfig();
            } else {
                updateMainConfigLastKnownModified();
            }
        } catch (Exception e) {
            mainConfig = createDefaultMainConfig();
            saveMainConfig();
        }
    }

    private static ShieldCapConfig parseMainConfigFile() throws IOException {
        try (FileReader reader = new FileReader(MAIN_CONFIG_FILE)) {
            ShieldCapConfig parsed = GSON.fromJson(reader, ShieldCapConfig.class);
            if (parsed == null) {
                throw new IOException("Parsed ShieldCap main config is null.");
            }
            return parsed;
        }
    }

    private static void saveMainConfig() {
        try (FileWriter writer = new FileWriter(MAIN_CONFIG_FILE)) {
            writer.write(formatMainConfigJson(mainConfig));
            writer.flush();
            updateMainConfigLastKnownModified();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void syncMainConfigFromDiskIfChanged() {
        long currentModified = MAIN_CONFIG_FILE.exists() ? MAIN_CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        if (currentModified == Long.MIN_VALUE || currentModified == mainConfigLastKnownModified) {
            return;
        }

        try {
            ShieldCapConfig parsed = parseMainConfigFile();
            boolean changed = normalizeMainConfig(parsed);
            mainConfig = parsed;
            if (changed) {
                saveMainConfig();
            } else {
                mainConfigLastKnownModified = currentModified;
            }
        } catch (Exception ignored) {
        }
    }

    private static void updateMainConfigLastKnownModified() {
        mainConfigLastKnownModified = MAIN_CONFIG_FILE.exists()
                ? MAIN_CONFIG_FILE.lastModified()
                : Long.MIN_VALUE;
    }

    private static String formatMainConfigJson(ShieldCapConfig config) {
        ShieldCapConfig source = config == null ? createDefaultMainConfig() : config;
        JsonObject root = new JsonObject();
        root.addProperty("Allow Shield Craft", !Boolean.FALSE.equals(source.allowShieldCraft));
        root.addProperty(
                "Shield keeps Signature Energy when swapped",
                !Boolean.FALSE.equals(source.shieldKeepsSignatureEnergyWhenSwapped)
        );
        root.addProperty(
                "Visual Shield in player back",
                !Boolean.FALSE.equals(source.visualShieldInPlayersBack)
        );
        return GSON.toJson(root);
    }

    private static boolean normalizeMainConfig(ShieldCapConfig target) {
        boolean changed = false;
        if (target.allowShieldCraft == null) {
            target.allowShieldCraft = true;
            changed = true;
        }
        if (target.shieldKeepsSignatureEnergyWhenSwapped == null) {
            target.shieldKeepsSignatureEnergyWhenSwapped = true;
            changed = true;
        }
        if (target.visualShieldInPlayersBack == null) {
            target.visualShieldInPlayersBack = true;
            changed = true;
        }
        return changed;
    }
}
