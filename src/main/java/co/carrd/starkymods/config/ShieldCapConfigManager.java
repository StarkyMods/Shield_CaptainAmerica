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
    private static final String CARTER_SHIELD_RECIPE_ID = "ShieldCap_CaptainCarter_Craft";
    private static final List<String> SHIELD_RECIPE_IDS =
            List.of(SHIELD_RECIPE_ID, VIBRANIUM_SHIELD_RECIPE_ID, CARTER_SHIELD_RECIPE_ID);
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

    public static boolean isFallResistanceWhenBlockingEnabled() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        return mainConfig == null || !Boolean.FALSE.equals(mainConfig.fallResistanceWhenBlocking);
    }

    public static boolean isDamageFromTheBackResistanceEnabled() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        return mainConfig == null || !Boolean.FALSE.equals(mainConfig.damageFromTheBackResistance);
    }

    public static ShieldCapConfig getMainConfigSnapshot() {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }

        ShieldCapConfig source = mainConfig == null ? createDefaultMainConfig() : mainConfig;
        ShieldCapConfig copy = new ShieldCapConfig();
        copy.allowShieldCraft = !Boolean.FALSE.equals(source.allowShieldCraft);
        copy.shieldKeepsSignatureEnergyWhenSwapped =
                !Boolean.FALSE.equals(source.shieldKeepsSignatureEnergyWhenSwapped);
        copy.visualShieldInPlayersBack = !Boolean.FALSE.equals(source.visualShieldInPlayersBack);
        copy.fallResistanceWhenBlocking = !Boolean.FALSE.equals(source.fallResistanceWhenBlocking);
        copy.damageFromTheBackResistance = !Boolean.FALSE.equals(source.damageFromTheBackResistance);
        copy.doubleJump = !Boolean.FALSE.equals(source.doubleJump);
        copy.fallStarAttack = !Boolean.FALSE.equals(source.fallStarAttack);
        copy.guardBash = !Boolean.FALSE.equals(source.guardBash);
        copy.guardBashPushLaunch = !Boolean.FALSE.equals(source.guardBashPushLaunch);
        copy.sprintAttack = !Boolean.FALSE.equals(source.sprintAttack);
        copy.kickPushAttack = !Boolean.FALSE.equals(source.kickPushAttack);
        copy.throwAttack = !Boolean.FALSE.equals(source.throwAttack);
        copy.throwLeftHand = !Boolean.FALSE.equals(source.throwLeftHand);
        copy.furiousOnslaught = !Boolean.FALSE.equals(source.furiousOnslaught);
        copy.guardBashShockwaveWithMjolnir = !Boolean.FALSE.equals(source.guardBashShockwaveWithMjolnir);
        return copy;
    }

    public static boolean isConfigCheckedInteractionEnabled(String interactionId) {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        ShieldCapConfig config = mainConfig == null ? createDefaultMainConfig() : mainConfig;
        String normalized = normalizeInteractionId(interactionId);
        return switch (normalized) {
            case "ShieldCap_Primary_Double_Jump" -> !Boolean.FALSE.equals(config.doubleJump);
            case "ShieldCap_Primary_Fallstar_Hit" ->
                    !Boolean.FALSE.equals(config.doubleJump) && !Boolean.FALSE.equals(config.fallStarAttack);
            case "ShieldCap_Secondary_Guard_Bash_Conditions" -> !Boolean.FALSE.equals(config.guardBash);
            case "ShieldCap_Guard_Dash_Push" -> !Boolean.FALSE.equals(config.guardBashPushLaunch);
            case "ShieldCap_Primary_Sprint_Hit" -> !Boolean.FALSE.equals(config.sprintAttack);
            case "ShieldCap_Kick_Push" -> !Boolean.FALSE.equals(config.kickPushAttack);
            case "ShieldCap_Primary_Throw_Conditions" -> !Boolean.FALSE.equals(config.throwAttack);
            case "ShieldCap_Secondary_Throw_Left_Conditions" -> !Boolean.FALSE.equals(config.throwLeftHand);
            case "ShieldCap_Signature_Furious_Onslaught" -> !Boolean.FALSE.equals(config.furiousOnslaught);
            case "ShieldCapLeft_Mjolnir_Guard_Bash_Conditions" ->
                    !Boolean.FALSE.equals(config.guardBashShockwaveWithMjolnir);
            default -> true;
        };
    }

    public static void saveMainConfigState(ShieldCapConfig updatedConfig) {
        syncMainConfigFromDiskIfChanged();
        if (mainConfig == null) {
            loadMainConfig();
        }
        if (mainConfig == null) {
            mainConfig = createDefaultMainConfig();
        }
        ShieldCapConfig source = updatedConfig == null ? createDefaultMainConfig() : updatedConfig;
        mainConfig.allowShieldCraft = source.allowShieldCraft;
        mainConfig.shieldKeepsSignatureEnergyWhenSwapped = source.shieldKeepsSignatureEnergyWhenSwapped;
        mainConfig.visualShieldInPlayersBack = source.visualShieldInPlayersBack;
        mainConfig.fallResistanceWhenBlocking = source.fallResistanceWhenBlocking;
        mainConfig.damageFromTheBackResistance = source.damageFromTheBackResistance;
        mainConfig.doubleJump = source.doubleJump;
        mainConfig.fallStarAttack = source.fallStarAttack;
        mainConfig.guardBash = source.guardBash;
        mainConfig.guardBashPushLaunch = source.guardBashPushLaunch;
        mainConfig.sprintAttack = source.sprintAttack;
        mainConfig.kickPushAttack = source.kickPushAttack;
        mainConfig.throwAttack = source.throwAttack;
        mainConfig.throwLeftHand = source.throwLeftHand;
        mainConfig.furiousOnslaught = source.furiousOnslaught;
        mainConfig.guardBashShockwaveWithMjolnir = source.guardBashShockwaveWithMjolnir;
        normalizeMainConfig(mainConfig);
        saveMainConfig();
    }

    public static void resetMainConfigToDefaults() {
        mainConfig = createDefaultMainConfig();
        saveMainConfig();
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
        config.fallResistanceWhenBlocking = true;
        config.damageFromTheBackResistance = true;
        config.doubleJump = true;
        config.fallStarAttack = true;
        config.guardBash = true;
        config.guardBashPushLaunch = true;
        config.sprintAttack = true;
        config.kickPushAttack = true;
        config.throwAttack = true;
        config.throwLeftHand = true;
        config.furiousOnslaught = true;
        config.guardBashShockwaveWithMjolnir = true;
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
        root.addProperty("Fall Resistance when blocking", !Boolean.FALSE.equals(source.fallResistanceWhenBlocking));
        root.addProperty("Damage from the Back Resistance", !Boolean.FALSE.equals(source.damageFromTheBackResistance));
        root.addProperty(
                "Double Jump (disabling this includes the FallStar Attack)",
                !Boolean.FALSE.equals(source.doubleJump)
        );
        root.addProperty("FallStar Attack", !Boolean.FALSE.equals(source.fallStarAttack));
        root.addProperty("Guard Bash", !Boolean.FALSE.equals(source.guardBash));
        root.addProperty("Guard Bash Push Launch", !Boolean.FALSE.equals(source.guardBashPushLaunch));
        root.addProperty("Sprint Attack", !Boolean.FALSE.equals(source.sprintAttack));
        root.addProperty("Kick Push Attack", !Boolean.FALSE.equals(source.kickPushAttack));
        root.addProperty("Throw", !Boolean.FALSE.equals(source.throwAttack));
        root.addProperty("Throw Left Hand", !Boolean.FALSE.equals(source.throwLeftHand));
        root.addProperty("Furious Onslaught", !Boolean.FALSE.equals(source.furiousOnslaught));
        root.addProperty(
                "Guard Bash Shockwave with Mjolnir",
                !Boolean.FALSE.equals(source.guardBashShockwaveWithMjolnir)
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
        if (target.fallResistanceWhenBlocking == null) {
            target.fallResistanceWhenBlocking = true;
            changed = true;
        }
        if (target.damageFromTheBackResistance == null) {
            target.damageFromTheBackResistance = true;
            changed = true;
        }
        if (target.doubleJump == null) {
            target.doubleJump = true;
            changed = true;
        }
        if (target.fallStarAttack == null) {
            target.fallStarAttack = true;
            changed = true;
        }
        if (target.guardBash == null) {
            target.guardBash = true;
            changed = true;
        }
        if (target.guardBashPushLaunch == null) {
            target.guardBashPushLaunch = true;
            changed = true;
        }
        if (target.sprintAttack == null) {
            target.sprintAttack = true;
            changed = true;
        }
        if (target.kickPushAttack == null) {
            target.kickPushAttack = true;
            changed = true;
        }
        if (target.throwAttack == null) {
            target.throwAttack = true;
            changed = true;
        }
        if (target.throwLeftHand == null) {
            target.throwLeftHand = true;
            changed = true;
        }
        if (target.furiousOnslaught == null) {
            target.furiousOnslaught = true;
            changed = true;
        }
        if (target.guardBashShockwaveWithMjolnir == null) {
            target.guardBashShockwaveWithMjolnir = true;
            changed = true;
        }
        return changed;
    }

    private static String normalizeInteractionId(String interactionId) {
        if (interactionId == null) {
            return "";
        }
        String trimmed = interactionId.trim();
        int separator = trimmed.lastIndexOf('.');
        return separator >= 0 && separator + 1 < trimmed.length()
                ? trimmed.substring(separator + 1)
                : trimmed;
    }
}
