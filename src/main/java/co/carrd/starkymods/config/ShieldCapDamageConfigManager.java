package co.carrd.starkymods.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShieldCapDamageConfigManager {
    private static final String DAMAGE_COMMENT = "If you wish, you can customize the damage inflicted by each Cap Shield attack to your liking.";
    private static final List<String> MOD_COMPATIBILITY_NOTE = Arrays.asList(
            "If true, this will automatically modify these damages depending on which",
            "other mods you have installed and are active.",
            "And every time you start the world/server this file will be rewritten.",
            "So if you want to keep your custom damages active, turn this to false."
    );
    private static final File FOLDER = ShieldCapConfigPaths.getFolder();
    private static final File DAMAGE_CONFIG_FILE = new File(FOLDER, "shieldcapdamages.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OLD_LAUNCH_FORCES_KEY = "LAUNCH FORCES";
    private static final String NEW_LAUNCH_FORCES_KEY = "KNOCKBACK AND LAUNCH FORCES";

    private static final List<String> ITEM_ASSET_PATHS = List.of(
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainAmerica_Starky.json",
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainCarter_Starky.json",
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_Vibranium_Starky.json",
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainAmerica_Starky.json",
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_CaptainCarter_Starky.json",
            "Server/Item/Items/Weapon/Shield/Weapon_ShieldLeft_Vibranium_Starky.json"
    );

    private static final List<DamageDefinition> DAMAGE_DEFINITIONS = List.of(
            damage("Swing 1", "Swing_Left_Damage"),
            damage("Spin Swing 2", "Swing_Spin_Left_Damage"),
            damage("Long Swing 3", "Long_Swing_Left_Damage"),
            damage("Long Spin Swing 4", "Long_Swing_Spin_Right_Damage"),
            damage("Swing Ground 5", "Swing_Down_Damage"),
            damage("Sprint Attack", "Sprint_Hit_Damage"),
            damage("Kick Push Attack", "Kick_Push_Damage"),
            damage("Guard Bash", "Guard_Bash_Damage"),
            damage("Guard Bash Push Launch", "Guard_Dash_Push_Damage"),
            damage("Crouch Spin Swing 1", "Crouch_Swing_Spin_Right_Damage"),
            damage("Crouch Uppercut Swing 2", "Crouch_UpperCut_Damage"),
            damage("Crouch Swing down from air 3", "Crouch_Jump_Hit_Damage"),
            damage("FallStar Attack", "FallStar_Hit_Damage"),
            projectileDamage("Throw", "ShieldCap_Throw_Damage", "Server/Item/Interactions/ShieldCap_Throw_Damage.json"),
            projectileDamage("Rethrow", "ShieldCap_Throw_Kick_Damage", "Server/Item/Interactions/ShieldCap_Throw_Kick_Damage.json"),
            damage("Furious Onslaught", "Furious_Onslaught_Damage"),
            damage("Left Hand Long Spin Swing", "Long_Swing_Spin_Left_Damage", "ShieldCap_Long_Swing_Spin_Left_Damage"),
            damage("Mjolnir+Shield Shockwave Guard Bash", "Guard_Dash_Mjolnir_Damage")
    );

    private static final List<DamageDefinition> LAUNCH_FORCE_DEFINITIONS = List.of(
            applyForce("Guard Bash Push Launch Force", "ShieldCap_Guard_Dash_Push", "Server/Item/Interactions/ShieldCap_Guard_Dash_Push.json"),
            knockback("Kick Push Attack Knockback Force", "Kick_Push_Knockback"),
            knockback("Guard Bash Knockback Force", "Guard_Bash_Knockback"),
            knockback("Guard Bash Push Launch Knockback Force", "Guard_Dash_Push_Knockback"),
            knockback("FallStar Attack Knockback Force", "FallStar_Hit_Knockback"),
            knockback("Furious Onslaught Knockback Force", "Furious_Onslaught_Knockback"),
            knockback("Perfect Parry Knockback Force", "PerfectParryShockwaveKnockback"),
            knockback("Mjolnir+Shield Shockwave Guard Bash Knockback Force", "Guard_Bash_Mjolnir_Knockback"),
            knockback("Mjolnir Impact on Shield Shockwave Knockback Force", "MjolnirShieldWieldImpactKnockback")
    );

    private static final List<String> ATTACK_PACK_DAMAGE_KEYS = List.of(
            "Swing 1",
            "Spin Swing 2",
            "Long Swing 3",
            "Long Spin Swing 4",
            "Swing Ground 5",
            "Sprint Attack",
            "Kick Push Attack",
            "Guard Bash",
            "Guard Bash Push Launch",
            "Crouch Spin Swing 1",
            "Crouch Uppercut Swing 2",
            "Crouch Swing down from air 3",
            "FallStar Attack",
            "Furious Onslaught",
            "Left Hand Long Spin Swing",
            "Mjolnir+Shield Shockwave Guard Bash"
    );

    private static ShieldCapDamages config;

    private ShieldCapDamageConfigManager() {
    }

    public static void init() {
        try {
            if (!FOLDER.exists()) {
                FOLDER.mkdirs();
            }
            if (!DAMAGE_CONFIG_FILE.exists()) {
                config = createDefault();
                save();
                return;
            }
            load();
        } catch (Exception e) {
            System.out.println("[ShieldCap] Error initializing shieldcapdamages.json:");
            e.printStackTrace();
        }
    }

    public static ShieldCapDamages getConfig() {
        if (config == null) {
            if (DAMAGE_CONFIG_FILE.exists()) {
                load();
            } else {
                config = createDefault();
                save();
            }
        }
        normalize(config);
        return config;
    }

    public static ShieldCapDamages getConfigSnapshot() {
        return cloneDamages(getConfig());
    }

    public static ShieldCapDamages createDamageConfigSnapshotForProfile(ShieldCapDamageCompatibilityProfile profile) {
        ShieldCapDamages snapshot = createDefault();
        snapshot.damageValues = createDamagesForProfile(profile == null ? ShieldCapDamageCompatibilityProfile.DEFAULT : profile);
        snapshot.modCompatibility = true;
        snapshot.modCompatibilityNote = new ArrayList<>(MOD_COMPATIBILITY_NOTE);
        snapshot.legacyDisableCompatibilityProfiles = null;
        return snapshot;
    }

    public static File getDamageConfigFile() {
        return DAMAGE_CONFIG_FILE;
    }

    public static List<DamageDefinition> getDamageDefinitions() {
        return DAMAGE_DEFINITIONS;
    }

    public static List<DamageDefinition> getLaunchForceDefinitions() {
        return LAUNCH_FORCE_DEFINITIONS;
    }

    public static List<DamageDefinition> getAllDefinitions() {
        List<DamageDefinition> all = new ArrayList<>();
        all.addAll(DAMAGE_DEFINITIONS);
        all.addAll(LAUNCH_FORCE_DEFINITIONS);
        return all;
    }

    public static void load() {
        try (FileReader reader = new FileReader(DAMAGE_CONFIG_FILE)) {
            ShieldCapDamages loaded = parseDamages(reader);
            config = loaded == null ? createDefault() : loaded;
            if (normalize(config)) {
                save();
            }
        } catch (Exception e) {
            config = createDefault();
            save();
        }
    }

    public static boolean reloadDamagesIfValid() {
        try (FileReader reader = new FileReader(DAMAGE_CONFIG_FILE)) {
            ShieldCapDamages parsed = parseDamages(reader);
            config = parsed == null ? createDefault() : parsed;
            if (normalize(config)) {
                save();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void saveDamagesFromUi(ShieldCapDamages updated) {
        config = updated == null ? createDefault() : cloneDamages(updated);
        normalize(config);
        save();
    }

    public static void resetDamagesToDefaults() {
        config = createDefault();
        normalize(config);
        save();
    }

    public static boolean applyDamageCompatibilityProfile(ShieldCapDamageCompatibilityProfile profile) {
        if (profile == null) {
            profile = ShieldCapDamageCompatibilityProfile.DEFAULT;
        }
        if (config == null) {
            load();
        }
        if (config == null) {
            config = createDefault();
        }
        Map<String, Double> desiredDamageValues = createDamagesForProfile(profile);
        boolean changed = !mapsEqual(config.damageValues, desiredDamageValues);
        config.damageValues = desiredDamageValues;
        if (normalize(config)) {
            changed = true;
        }
        if (changed) {
            save();
        }
        return changed;
    }

    public static boolean isDamageCompatibilityProfileEnabled() {
        if (config == null) {
            load();
        }
        return config == null || !Boolean.FALSE.equals(config.modCompatibility);
    }

    public static boolean disableDamageCompatibilityProfileAndSave() {
        if (config == null) {
            load();
        }
        if (config == null || Boolean.FALSE.equals(config.modCompatibility)) {
            return false;
        }
        config.modCompatibility = false;
        config.modCompatibilityNote = new ArrayList<>(MOD_COMPATIBILITY_NOTE);
        config.legacyDisableCompatibilityProfiles = null;
        save();
        return true;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(DAMAGE_CONFIG_FILE)) {
            writer.write(formatDamageJson(config));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ShieldCapDamages createDefault() {
        ShieldCapDamages defaults = new ShieldCapDamages();
        defaults.comment = DAMAGE_COMMENT;
        defaults.damageValues = createDefaultsFor(DAMAGE_DEFINITIONS);
        defaults.launchForces = createDefaultsFor(LAUNCH_FORCE_DEFINITIONS);
        defaults.modCompatibility = true;
        defaults.modCompatibilityNote = new ArrayList<>(MOD_COMPATIBILITY_NOTE);
        return defaults;
    }

    private static Map<String, Double> createDamagesForProfile(ShieldCapDamageCompatibilityProfile profile) {
        Map<String, Double> defaults = createDefaultsFor(DAMAGE_DEFINITIONS);
        Map<String, Double> desired = new LinkedHashMap<>();
        for (DamageDefinition definition : DAMAGE_DEFINITIONS) {
            String key = definition.displayKey();
            double baseValue = defaults.getOrDefault(key, 0.0);
            desired.put(key, applyProfileMultiplier(profile, key, baseValue));
        }
        return desired;
    }

    private static double applyProfileMultiplier(ShieldCapDamageCompatibilityProfile profile, String key, double baseValue) {
        if (profile == null) {
            profile = ShieldCapDamageCompatibilityProfile.DEFAULT;
        }
        return switch (profile) {
            case ENDLESS_OR_ENDGAME -> {
                if ("Throw".equals(key)) {
                    yield baseValue * 2.4;
                }
                if ("Rethrow".equals(key)) {
                    yield baseValue * 1.8;
                }
                if (ATTACK_PACK_DAMAGE_KEYS.contains(key)) {
                    yield baseValue * 1.9;
                }
                yield baseValue;
            }
            case MAJOR_DUNGEONS -> {
                if ("Throw".equals(key)) {
                    yield baseValue * 2.0;
                }
                if ("Rethrow".equals(key)) {
                    yield baseValue * 1.5;
                }
                if (ATTACK_PACK_DAMAGE_KEYS.contains(key)) {
                    yield baseValue * 1.2;
                }
                yield baseValue;
            }
            case WANS_WONDER_WEAPON -> {
                if ("Throw".equals(key)) {
                    yield baseValue * 2.5;
                }
                if ("Rethrow".equals(key)) {
                    yield baseValue * 2.0;
                }
                if (ATTACK_PACK_DAMAGE_KEYS.contains(key)) {
                    yield baseValue * 2.6;
                }
                yield baseValue;
            }
            case DEFAULT -> baseValue;
        };
    }

    private static Map<String, Double> createDefaultsFor(List<DamageDefinition> definitions) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (DamageDefinition definition : definitions) {
            values.put(definition.displayKey(), readDefaultValue(definition));
        }
        return values;
    }

    private static boolean normalize(ShieldCapDamages target) {
        if (target == null) {
            return false;
        }
        boolean changed = false;
        if (target.comment == null || target.comment.isBlank()) {
            target.comment = DAMAGE_COMMENT;
            changed = true;
        }
        if (target.modCompatibility == null) {
            if (target.legacyDisableCompatibilityProfiles != null) {
                target.modCompatibility = !target.legacyDisableCompatibilityProfiles;
            } else {
                target.modCompatibility = true;
            }
            changed = true;
        }
        if (target.modCompatibilityNote == null || target.modCompatibilityNote.isEmpty()) {
            target.modCompatibilityNote = new ArrayList<>(MOD_COMPATIBILITY_NOTE);
            changed = true;
        }
        if (target.legacyDisableCompatibilityProfiles != null) {
            target.legacyDisableCompatibilityProfiles = null;
            changed = true;
        }
        if (target.schemaVersion == null || target.schemaVersion < 2) {
            target.schemaVersion = 2;
            changed = true;
            if (target.damageValues != null) {
                migrateExternalDefault(target.damageValues, "Throw", DAMAGE_DEFINITIONS);
                migrateExternalDefault(target.damageValues, "Rethrow", DAMAGE_DEFINITIONS);
            }
            if (target.launchForces != null) {
                migrateExternalDefault(target.launchForces, "Guard Bash Push Launch Force", LAUNCH_FORCE_DEFINITIONS);
            }
        }
        if (target.damageValues != null && target.damageValues.containsKey("Long Spìn Swing 4")
                && !target.damageValues.containsKey("Long Spin Swing 4")) {
            target.damageValues.put("Long Spin Swing 4", target.damageValues.get("Long Spìn Swing 4"));
            changed = true;
        }
        if (target.damageValues == null) {
            target.damageValues = new LinkedHashMap<>();
            changed = true;
        }
        if (target.launchForces == null) {
            target.launchForces = new LinkedHashMap<>();
            changed = true;
        }
        changed |= normalizeSection(target.damageValues, DAMAGE_DEFINITIONS);
        changed |= normalizeSection(target.launchForces, LAUNCH_FORCE_DEFINITIONS);
        return changed;
    }

    private static boolean normalizeSection(Map<String, Double> values, List<DamageDefinition> definitions) {
        boolean changed = false;
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (DamageDefinition definition : definitions) {
            Double value = values.get(definition.displayKey());
            if (value == null || value.isNaN() || value.isInfinite()) {
                value = readDefaultValue(definition);
                changed = true;
            }
            normalized.put(definition.displayKey(), Math.max(0.0, value));
        }
        if (!normalized.equals(values)) {
            values.clear();
            values.putAll(normalized);
            changed = true;
        }
        return changed;
    }

    private static void migrateExternalDefault(Map<String, Double> values, String displayKey, List<DamageDefinition> definitions) {
        if (values == null || displayKey == null) {
            return;
        }
        Double current = values.get(displayKey);
        if (current != null && Math.abs(current) > 0.000001) {
            return;
        }
        for (DamageDefinition definition : definitions) {
            if (displayKey.equals(definition.displayKey())) {
                values.put(displayKey, readDefaultValue(definition));
                return;
            }
        }
    }

    private static ShieldCapDamages cloneDamages(ShieldCapDamages source) {
        ShieldCapDamages copy = new ShieldCapDamages();
        if (source == null) {
            return copy;
        }
        copy.comment = source.comment;
        copy.schemaVersion = source.schemaVersion;
        copy.modCompatibility = source.modCompatibility;
        copy.modCompatibilityNote = source.modCompatibilityNote == null
                ? new ArrayList<>()
                : new ArrayList<>(source.modCompatibilityNote);
        copy.legacyDisableCompatibilityProfiles = source.legacyDisableCompatibilityProfiles;
        copy.damageValues = source.damageValues == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.damageValues);
        copy.launchForces = source.launchForces == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.launchForces);
        return copy;
    }

    private static double readDefaultValue(DamageDefinition definition) {
        if (definition == null || definition.type() == ValueType.PLACEHOLDER) {
            return 0.0;
        }
        if (definition.assetPath() != null) {
            JsonObject assetJson = loadJson(definition.assetPath());
            Double assetValue = readExternalAssetValue(assetJson, definition.type());
            return assetValue == null ? 0.0 : assetValue;
        }
        for (String assetPath : ITEM_ASSET_PATHS) {
            JsonObject item = loadJson(assetPath);
            if (item == null) {
                continue;
            }
            JsonObject vars = item.getAsJsonObject("InteractionVars");
            if (vars == null) {
                continue;
            }
            for (String varKey : definition.interactionVars()) {
                Double value = readValue(vars, varKey, definition.type());
                if (value != null) {
                    return value;
                }
            }
        }
        return 0.0;
    }

    private static Double readExternalAssetValue(JsonObject assetJson, ValueType type) {
        return switch (type) {
            case PROJECTILE_DAMAGE -> readProjectileDamage(assetJson);
            case APPLY_FORCE -> readApplyForce(assetJson);
            default -> null;
        };
    }

    private static Double readValue(JsonObject interactionVars, String varKey, ValueType type) {
        if (interactionVars == null || varKey == null || !interactionVars.has(varKey)) {
            return null;
        }
        JsonObject var = interactionVars.getAsJsonObject(varKey);
        JsonArray interactions = var == null ? null : var.getAsJsonArray("Interactions");
        if (interactions == null || interactions.isEmpty() || !interactions.get(0).isJsonObject()) {
            return null;
        }
        JsonObject interaction = interactions.get(0).getAsJsonObject();
        return switch (type) {
            case DAMAGE -> readPhysicalDamage(interaction);
            case KNOCKBACK -> readKnockbackForce(interaction);
            default -> null;
        };
    }

    private static Double readPhysicalDamage(JsonObject interaction) {
        JsonObject damageCalculator = interaction == null ? null : interaction.getAsJsonObject("DamageCalculator");
        JsonObject baseDamage = damageCalculator == null ? null : damageCalculator.getAsJsonObject("BaseDamage");
        if (baseDamage == null || !baseDamage.has("Physical")) {
            return null;
        }
        try {
            return baseDamage.get("Physical").getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double readProjectileDamage(JsonObject interaction) {
        JsonObject damageCalculator = interaction == null ? null : interaction.getAsJsonObject("DamageCalculator");
        JsonObject baseDamage = damageCalculator == null ? null : damageCalculator.getAsJsonObject("BaseDamage");
        if (baseDamage == null || !baseDamage.has("Projectile")) {
            return null;
        }
        try {
            return baseDamage.get("Projectile").getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double readApplyForce(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("Type") && "ApplyForce".equals(object.get("Type").getAsString()) && object.has("Force")) {
                try {
                    return object.get("Force").getAsDouble();
                } catch (Exception ignored) {
                    return null;
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Double value = readApplyForce(entry.getValue());
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Double value = readApplyForce(child);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Double readKnockbackForce(JsonObject interaction) {
        JsonObject damageEffects = interaction == null ? null : interaction.getAsJsonObject("DamageEffects");
        JsonObject knockback = damageEffects == null ? null : damageEffects.getAsJsonObject("Knockback");
        if (knockback == null || !knockback.has("Force")) {
            return null;
        }
        try {
            return knockback.get("Force").getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject loadJson(String path) {
        try (InputStream stream = ShieldCapDamageConfigManager.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatDamageJson(ShieldCapDamages value) {
        JsonObject root = JsonParser.parseString(GSON.toJson(value)).getAsJsonObject();
        return GSON.toJson(root);
    }

    private static ShieldCapDamages parseDamages(FileReader reader) throws IOException {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.has(OLD_LAUNCH_FORCES_KEY) && !root.has(NEW_LAUNCH_FORCES_KEY)) {
            root.add(NEW_LAUNCH_FORCES_KEY, root.get(OLD_LAUNCH_FORCES_KEY));
            root.remove(OLD_LAUNCH_FORCES_KEY);
        }
        migrateLegacyModCompatibilityNote(root);
        return GSON.fromJson(root, ShieldCapDamages.class);
    }

    private static void migrateLegacyModCompatibilityNote(JsonObject root) {
        if (root == null || !root.has("Note")) {
            return;
        }
        JsonElement noteElement = root.get("Note");
        if (noteElement == null || noteElement.isJsonNull() || noteElement.isJsonArray()) {
            return;
        }
        JsonArray noteLines = new JsonArray();
        if (noteElement.isJsonPrimitive() && noteElement.getAsJsonPrimitive().isString()) {
            String[] lines = noteElement.getAsString().split("\\\\n|\\n");
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (!trimmed.isEmpty()) {
                    noteLines.add(trimmed);
                }
            }
        }
        if (noteLines.isEmpty()) {
            for (String line : MOD_COMPATIBILITY_NOTE) {
                noteLines.add(line);
            }
        }
        root.add("Note", noteLines);
    }

    private static boolean mapsEqual(Map<String, Double> left, Map<String, Double> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, Double> entry : right.entrySet()) {
            Double leftValue = left.get(entry.getKey());
            if (leftValue == null || Math.abs(leftValue - entry.getValue()) > 0.0001) {
                return false;
            }
        }
        return true;
    }

    private static DamageDefinition damage(String displayKey, String... interactionVars) {
        return new DamageDefinition(displayKey, ValueType.DAMAGE, List.of(interactionVars), null, null);
    }

    private static DamageDefinition knockback(String displayKey, String... interactionVars) {
        return new DamageDefinition(displayKey, ValueType.KNOCKBACK, List.of(interactionVars), null, null);
    }

    private static DamageDefinition placeholder(String displayKey) {
        return new DamageDefinition(displayKey, ValueType.PLACEHOLDER, List.of(), null, null);
    }

    private static DamageDefinition projectileDamage(String displayKey, String assetId, String assetPath) {
        return new DamageDefinition(displayKey, ValueType.PROJECTILE_DAMAGE, List.of(), assetId, assetPath);
    }

    private static DamageDefinition applyForce(String displayKey, String assetId, String assetPath) {
        return new DamageDefinition(displayKey, ValueType.APPLY_FORCE, List.of(), assetId, assetPath);
    }

    public record DamageDefinition(String displayKey, ValueType type, List<String> interactionVars, String assetId, String assetPath) {
    }

    public enum ValueType {
        DAMAGE,
        KNOCKBACK,
        PROJECTILE_DAMAGE,
        APPLY_FORCE,
        PLACEHOLDER
    }
}
