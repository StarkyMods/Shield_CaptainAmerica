package co.carrd.starkymods.config;

import co.carrd.starkymods.compat.ShieldCapOptionalModSupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ShieldCapCraftConfigManager {
    private static final String CRAFT_COMMENT = "If you wish, customize Cap Shield crafting recipe to your liking.";
    private static final List<String> CRAFT_MOD_COMPATIBILITY_NOTE = Arrays.asList(
            "If true, this will automatically modify some of these ingredients",
            "and quantities if EndgameQoL mod is installed and is active.",
            "And every time you start the world/server this file will be rewritten.",
            "So if you want to keep your custom recipe active, turn this to false."
    );
    private static final File FOLDER = ShieldCapConfigPaths.getFolder();
    private static final File CRAFT_CONFIG_FILE = new File(FOLDER, "shieldcapcraft.json");
    private static final String SHIELD_RECIPE_ID = "ShieldCap_Craft";
    private static final String VIBRANIUM_SHIELD_RECIPE_ID = "ShieldCap_Vibranium_Craft";
    private static final String CARTER_SHIELD_RECIPE_ID = "ShieldCap_CaptainCarter_Craft";
    private static final String GEORGIO_SHIELD_RECIPE_ID = "ShieldCap_Georgio_Craft";
    private static final String ANTI_CAPTAIN_AMERICA_SHIELD_RECIPE_ID = "ShieldCap_AntiCaptainAmerica_Craft";
    private static final String NORMAL_BENCH_ID = "Armor_Bench";
    private static final String NORMAL_BENCH_CATEGORY = "Weapon_Shield";
    private static final String ENDGAME_BENCH_ID = "Endgame_Bench";
    private static final String ENDGAME_BENCH_CATEGORY = "Endgame_Armor_Prisma";
    private static final List<String> SHIELD_RECIPE_IDS =
            List.of(SHIELD_RECIPE_ID, VIBRANIUM_SHIELD_RECIPE_ID, CARTER_SHIELD_RECIPE_ID, GEORGIO_SHIELD_RECIPE_ID);
    private static final String PRIMARY_WEAPON_ASSET_PATH =
            "Server/Item/Items/Weapon/Shield/Weapon_Shield_CaptainAmerica_Starky.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ShieldCapCraft config;

    private ShieldCapCraftConfigManager() {
    }

    public static void init() {
        try {
            if (!FOLDER.exists()) {
                FOLDER.mkdirs();
            }

            if (!CRAFT_CONFIG_FILE.exists()) {
                config = createDefault();
                save();
                return;
            }

            load();
        } catch (Exception e) {
            System.out.println("[ShieldCap] Error initializing shieldcapcraft.json:");
            e.printStackTrace();
        }
    }

    public static ShieldCapCraft getConfig() {
        if (config == null) {
            if (CRAFT_CONFIG_FILE.exists()) {
                load();
            } else {
                config = createDefault();
            }
        }
        ensureCraftSchemaUpToDate();
        return config;
    }

    public static ShieldCapCraft getConfigSnapshot() {
        return cloneCraft(getConfig());
    }

    public static ShieldCapCraft createDefaultCraftConfigSnapshot() {
        return createDefault();
    }

    public static File getCraftConfigFile() {
        return CRAFT_CONFIG_FILE;
    }

    public static String getShieldRecipeId() {
        return SHIELD_RECIPE_ID;
    }

    public static List<String> getShieldRecipeIds() {
        if (!ShieldCapOptionalModSupport.isCaptainAmericaSuitActive()) {
            return SHIELD_RECIPE_IDS;
        }

        List<String> recipeIds = new ArrayList<>(SHIELD_RECIPE_IDS);
        recipeIds.add(ANTI_CAPTAIN_AMERICA_SHIELD_RECIPE_ID);
        return recipeIds;
    }

    public static String getAntiCaptainAmericaShieldRecipeId() {
        return ANTI_CAPTAIN_AMERICA_SHIELD_RECIPE_ID;
    }

    public static void load() {
        try {
            ShieldCapCraft loaded;
            try (FileReader reader = new FileReader(CRAFT_CONFIG_FILE)) {
                loaded = GSON.fromJson(reader, ShieldCapCraft.class);
            }
            config = loaded == null ? createDefault() : loaded;
            if (normalizeCraft(config)) {
                save();
            }
        } catch (Exception e) {
            config = createDefault();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CRAFT_CONFIG_FILE)) {
            writer.write(formatCraftJson(config));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveCraftState(ShieldCapCraft updatedConfig) {
        saveCraftState(updatedConfig, true);
    }

    public static void saveCraftState(ShieldCapCraft updatedConfig, boolean autoDisableCompatibilityOnValueChange) {
        ShieldCapCraft previous = cloneCraft(config);
        config = updatedConfig == null ? createDefault() : cloneCraft(updatedConfig);
        normalizeCraft(config);
        if (autoDisableCompatibilityOnValueChange
                && craftRecipeFieldsChanged(previous, config)
                && !Boolean.FALSE.equals(config.modCompatibility)) {
            config.modCompatibility = false;
            config.modCompatibilityNote = new ArrayList<>(CRAFT_MOD_COMPATIBILITY_NOTE);
        }
        save();
    }

    public static void resetCraftToDefaults() {
        config = createDefault();
        normalizeCraft(config);
        save();
    }

    public static boolean reloadCraftIfValid() {
        try {
            ShieldCapCraft parsed = parseCraftFile();
            boolean changed = normalizeCraft(parsed);
            config = parsed;
            if (changed) {
                save();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isCraftCompatibilityProfileEnabled() {
        return getConfig() == null || !Boolean.FALSE.equals(getConfig().modCompatibility);
    }

    public static boolean disableCraftCompatibilityProfileIfRecipeChangedAndSave(ShieldCapCraft previous) {
        if (config == null) {
            load();
        }
        if (config == null
                || Boolean.FALSE.equals(config.modCompatibility)
                || !craftRecipeFieldsChanged(previous, config)) {
            return false;
        }

        config.modCompatibility = false;
        config.modCompatibilityNote = new ArrayList<>(CRAFT_MOD_COMPATIBILITY_NOTE);
        save();
        return true;
    }

    public static boolean hasEndgameBenchRequirement() {
        return findBench(getConfig(), ENDGAME_BENCH_ID) != null;
    }

    public static boolean applyEndgameCraftCompatibilityProfile() {
        ShieldCapCraft current = getConfig();
        if (current == null) {
            current = createDefault();
        }

        ShieldCapCraft desiredCraft = cloneCraft(current);
        desiredCraft.Input = new ArrayList<>();
        desiredCraft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Prisma", 50));
        desiredCraft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Onyxium", 5));
        desiredCraft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Mithril", 10));
        desiredCraft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Leather_Prismic", 15));
        desiredCraft.Input.add(new ShieldCapCraft.IngredientEntry("Alpha_Rex_Leather", 1));
        ShieldCapCraft.BenchConfig normalBench = findBench(current, NORMAL_BENCH_ID);
        if (normalBench == null) {
            ShieldCapCraft defaults = createDefault();
            normalBench = findBench(defaults, NORMAL_BENCH_ID);
        }
        if (normalBench == null) {
            normalBench = createBench(NORMAL_BENCH_ID, 3, NORMAL_BENCH_CATEGORY);
        }

        ShieldCapCraft.BenchConfig endgameBench = findBench(current, ENDGAME_BENCH_ID);
        if (endgameBench == null) {
            endgameBench = createBench(ENDGAME_BENCH_ID, 0, ENDGAME_BENCH_CATEGORY);
        }

        desiredCraft.BenchRequirements = new ArrayList<>();
        desiredCraft.BenchRequirements.add(cloneBench(normalBench));
        desiredCraft.BenchRequirements.add(cloneBench(endgameBench));
        desiredCraft.Bench = cloneBench(normalBench);

        boolean changed = !craftRecipeFieldsEqual(current, desiredCraft);
        current.Input = cloneIngredients(desiredCraft.Input);
        current.TimeSeconds = desiredCraft.TimeSeconds;
        current.RequiredMemoriesLevel = desiredCraft.RequiredMemoriesLevel;
        current.BenchRequirements = cloneBenchList(desiredCraft.BenchRequirements);
        current.Bench = cloneBench(desiredCraft.Bench);
        current.modCompatibility = true;
        current.modCompatibilityNote = new ArrayList<>(CRAFT_MOD_COMPATIBILITY_NOTE);

        if (normalizeCraft(current)) {
            changed = true;
        }
        if (changed) {
            save();
        }
        return changed;
    }

    private static ShieldCapCraft createDefault() {
        ShieldCapCraft fromRecipe = createDefaultFromRecipeAsset();
        ShieldCapCraft craft = fromRecipe != null ? fromRecipe : createDefaultHardcoded();
        if (craft.comment == null || craft.comment.trim().isEmpty()) {
            craft.comment = CRAFT_COMMENT;
        }
        if (craft.weaponMaxDurability == null) {
            craft.weaponMaxDurability = readDefaultWeaponMaxDurability();
        }
        if (craft.modCompatibility == null) {
            craft.modCompatibility = true;
        }
        if (craft.modCompatibilityNote == null || craft.modCompatibilityNote.isEmpty()) {
            craft.modCompatibilityNote = new ArrayList<>(CRAFT_MOD_COMPATIBILITY_NOTE);
        }
        return craft;
    }

    private static ShieldCapCraft createDefaultFromRecipeAsset() {
        String recipeAssetPath = "Server/Item/Recipes/" + SHIELD_RECIPE_ID + ".json";

        try (InputStream stream = ShieldCapCraftConfigManager.class.getClassLoader().getResourceAsStream(recipeAssetPath)) {
            if (stream == null) {
                return null;
            }

            JsonObject recipe = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            ShieldCapCraft craft = new ShieldCapCraft();

            JsonArray input = recipe.getAsJsonArray("Input");
            if (input != null) {
                for (JsonElement element : input) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject ingredient = element.getAsJsonObject();
                    if (!ingredient.has("ItemId")) {
                        continue;
                    }
                    String itemId = ingredient.get("ItemId").getAsString();
                    int quantity = ingredient.has("Quantity") ? ingredient.get("Quantity").getAsInt() : 1;
                    craft.Input.add(new ShieldCapCraft.IngredientEntry(itemId, quantity));
                }
            }

            if (recipe.has("TimeSeconds")) {
                craft.TimeSeconds = recipe.get("TimeSeconds").getAsInt();
            }
            if (recipe.has("RequiredMemoriesLevel")) {
                craft.RequiredMemoriesLevel = recipe.get("RequiredMemoriesLevel").getAsInt();
            }

            JsonArray benchRequirements = recipe.getAsJsonArray("BenchRequirement");
            if (benchRequirements != null) {
                for (JsonElement element : benchRequirements) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject bench = element.getAsJsonObject();
                    ShieldCapCraft.BenchConfig benchConfig = new ShieldCapCraft.BenchConfig();
                    if (bench.has("Id")) {
                        benchConfig.Id = bench.get("Id").getAsString();
                    }
                    if (bench.has("RequiredTierLevel")) {
                        benchConfig.RequiredTierLevel = bench.get("RequiredTierLevel").getAsInt();
                    }

                    benchConfig.Categories.clear();
                    JsonArray categories = bench.getAsJsonArray("Categories");
                    if (categories != null) {
                        for (JsonElement category : categories) {
                            benchConfig.Categories.add(category.getAsString());
                        }
                    }
                    craft.BenchRequirements.add(benchConfig);
                }
            }

            if (!craft.BenchRequirements.isEmpty()) {
                craft.Bench = cloneBench(craft.BenchRequirements.get(0));
            }

            return craft;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ShieldCapCraft createDefaultHardcoded() {
        ShieldCapCraft craft = new ShieldCapCraft();
        craft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Adamantite", 120));
        craft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Fabric_Scrap_Cindercloth", 20));
        craft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Iron", 50));
        craft.Input.add(new ShieldCapCraft.IngredientEntry("Ingredient_Bar_Cobalt", 10));
        craft.Bench = createBench(NORMAL_BENCH_ID, 3, NORMAL_BENCH_CATEGORY);
        craft.BenchRequirements.add(cloneBench(craft.Bench));
        craft.TimeSeconds = 4;
        craft.RequiredMemoriesLevel = 2;
        return craft;
    }

    private static ShieldCapCraft parseCraftFile() throws IOException {
        try (FileReader reader = new FileReader(CRAFT_CONFIG_FILE)) {
            ShieldCapCraft parsed = GSON.fromJson(reader, ShieldCapCraft.class);
            if (parsed == null) {
                throw new IOException("Parsed ShieldCap craft config is null.");
            }
            return parsed;
        }
    }

    private static boolean normalizeCraft(ShieldCapCraft target) {
        boolean changed = false;
        ShieldCapCraft defaults = createDefault();

        if (target.comment == null || target.comment.trim().isEmpty()) {
            target.comment = defaults.comment;
            changed = true;
        }
        if (target.weaponMaxDurability == null) {
            target.weaponMaxDurability = defaults.weaponMaxDurability;
            changed = true;
        } else if (target.weaponMaxDurability < 0) {
            target.weaponMaxDurability = 0;
            changed = true;
        }
        if (target.modCompatibility == null) {
            target.modCompatibility = true;
            changed = true;
        }
        if (target.modCompatibilityNote == null || target.modCompatibilityNote.isEmpty()) {
            target.modCompatibilityNote = new ArrayList<>(CRAFT_MOD_COMPATIBILITY_NOTE);
            changed = true;
        }

        if (target.Input == null) {
            target.Input = cloneIngredients(defaults.Input);
            changed = true;
        }
        if (target.TimeSeconds <= 0) {
            target.TimeSeconds = defaults.TimeSeconds;
            changed = true;
        }
        if (target.RequiredMemoriesLevel < 0) {
            target.RequiredMemoriesLevel = defaults.RequiredMemoriesLevel;
            changed = true;
        }
        if (target.BenchRequirements == null) {
            target.BenchRequirements = new ArrayList<>();
            changed = true;
        }
        if (target.Bench == null) {
            target.Bench = cloneBench(defaults.Bench);
            changed = true;
        } else {
            if (target.Bench.Id == null || target.Bench.Id.trim().isEmpty()) {
                target.Bench.Id = defaults.Bench.Id;
                changed = true;
            }
            if (target.Bench.RequiredTierLevel < 0) {
                target.Bench.RequiredTierLevel = defaults.Bench.RequiredTierLevel;
                changed = true;
            }
            if (target.Bench.Categories == null) {
                target.Bench.Categories = new ArrayList<>(defaults.Bench.Categories);
                changed = true;
            }
        }

        if (target.BenchRequirements.isEmpty()) {
            target.BenchRequirements = new ArrayList<>();
            target.BenchRequirements.add(cloneBench(target.Bench));
            changed = true;
        } else {
            if (normalizeBenchList(target.BenchRequirements, defaults.BenchRequirements)) {
                changed = true;
            }
            if (findBenchInList(target.BenchRequirements, ENDGAME_BENCH_ID) != null
                    && findBenchInList(target.BenchRequirements, NORMAL_BENCH_ID) == null) {
                ShieldCapCraft.BenchConfig normalBench = findBench(defaults, NORMAL_BENCH_ID);
                target.BenchRequirements.add(0, normalBench == null
                        ? createBench(NORMAL_BENCH_ID, 3, NORMAL_BENCH_CATEGORY)
                        : cloneBench(normalBench));
                changed = true;
            }
            if (!benchEquals(target.Bench, target.BenchRequirements.get(0))) {
                target.Bench = cloneBench(target.BenchRequirements.get(0));
                changed = true;
            }
        }

        return changed;
    }

    private static String formatCraftJson(ShieldCapCraft craft) {
        ShieldCapCraft source = craft == null ? createDefault() : craft;
        JsonObject root = new JsonObject();
        root.addProperty("Comment", source.comment == null || source.comment.isBlank() ? CRAFT_COMMENT : source.comment);
        root.addProperty("Weapon Max Durability", source.weaponMaxDurability == null ? 0 : Math.max(0, source.weaponMaxDurability));
        root.addProperty("Mod Compatibility", source.modCompatibility == null || source.modCompatibility);
        JsonArray note = new JsonArray();
        List<String> noteLines = source.modCompatibilityNote == null || source.modCompatibilityNote.isEmpty()
                ? CRAFT_MOD_COMPATIBILITY_NOTE
                : source.modCompatibilityNote;
        for (String line : noteLines) {
            if (line != null) {
                note.add(line);
            }
        }
        root.add("Note", note);

        JsonArray input = new JsonArray();
        if (source.Input != null) {
            for (ShieldCapCraft.IngredientEntry entry : source.Input) {
                if (entry == null || entry.ItemId == null || entry.ItemId.isBlank()) {
                    continue;
                }
                JsonObject ingredient = new JsonObject();
                ingredient.addProperty("ItemId", entry.ItemId);
                ingredient.addProperty("Quantity", Math.max(1, entry.Quantity));
                input.add(ingredient);
            }
        }
        root.add("Input", input);
        root.addProperty("TimeSeconds", source.TimeSeconds);
        root.addProperty("RequiredMemoriesLevel", source.RequiredMemoriesLevel);

        JsonArray benches = new JsonArray();
        for (ShieldCapCraft.BenchConfig bench : getEffectiveBenches(source)) {
            if (bench == null) {
                continue;
            }
            JsonObject benchJson = new JsonObject();
            benchJson.addProperty("Id", bench.Id);
            benchJson.addProperty("RequiredTierLevel", bench.RequiredTierLevel);
            JsonArray categories = new JsonArray();
            if (bench.Categories != null) {
                for (String category : bench.Categories) {
                    if (category != null && !category.isBlank()) {
                        categories.add(category);
                    }
                }
            }
            benchJson.add("Categories", categories);
            benches.add(benchJson);
        }
        root.add("Bench Requirements", benches);

        JsonObject bench = new JsonObject();
        ShieldCapCraft.BenchConfig primaryBench = source.Bench == null ? new ShieldCapCraft.BenchConfig() : source.Bench;
        bench.addProperty("Id", primaryBench.Id);
        bench.addProperty("RequiredTierLevel", primaryBench.RequiredTierLevel);
        JsonArray benchCategories = new JsonArray();
        if (primaryBench.Categories != null) {
            for (String category : primaryBench.Categories) {
                if (category != null && !category.isBlank()) {
                    benchCategories.add(category);
                }
            }
        }
        bench.add("Categories", benchCategories);
        root.add("Bench", bench);
        return GSON.toJson(root);
    }

    private static void ensureCraftSchemaUpToDate() {
        if (config == null) {
            return;
        }
        try {
            if (normalizeCraft(config)) {
                save();
            }
        } catch (Exception ignored) {
        }
    }

    private static int readDefaultWeaponMaxDurability() {
        try (InputStream stream = ShieldCapCraftConfigManager.class.getClassLoader().getResourceAsStream(PRIMARY_WEAPON_ASSET_PATH)) {
            if (stream == null) {
                return 0;
            }
            JsonObject weapon = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            if (!weapon.has("MaxDurability")) {
                return 0;
            }
            return Math.max(0, weapon.get("MaxDurability").getAsInt());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean normalizeBenchList(List<ShieldCapCraft.BenchConfig> benches,
                                              List<ShieldCapCraft.BenchConfig> defaultBenches) {
        boolean changed = false;
        for (int i = 0; i < benches.size(); i++) {
            ShieldCapCraft.BenchConfig bench = benches.get(i);
            ShieldCapCraft.BenchConfig fallback = defaultBenches.isEmpty()
                    ? new ShieldCapCraft.BenchConfig()
                    : defaultBenches.get(Math.min(i, defaultBenches.size() - 1));

            if (bench == null) {
                benches.set(i, cloneBench(fallback));
                changed = true;
                continue;
            }
            if (bench.Id == null || bench.Id.trim().isEmpty()) {
                bench.Id = fallback.Id;
                changed = true;
            }
            if (bench.RequiredTierLevel < 0) {
                bench.RequiredTierLevel = fallback.RequiredTierLevel;
                changed = true;
            }
            if (bench.Categories == null) {
                bench.Categories = new ArrayList<>(fallback.Categories);
                changed = true;
            }
        }
        return changed;
    }

    private static ShieldCapCraft.BenchConfig createBench(String id, int requiredTierLevel, String... categories) {
        ShieldCapCraft.BenchConfig bench = new ShieldCapCraft.BenchConfig();
        bench.Id = id;
        bench.RequiredTierLevel = requiredTierLevel;
        bench.Categories = new ArrayList<>();
        if (categories != null) {
            for (String category : categories) {
                if (category != null && !category.isBlank()) {
                    bench.Categories.add(category);
                }
            }
        }
        return bench;
    }

    private static List<ShieldCapCraft.IngredientEntry> cloneIngredients(List<ShieldCapCraft.IngredientEntry> source) {
        List<ShieldCapCraft.IngredientEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (ShieldCapCraft.IngredientEntry entry : source) {
            if (entry == null) {
                continue;
            }
            copy.add(new ShieldCapCraft.IngredientEntry(entry.ItemId, entry.Quantity));
        }
        return copy;
    }

    private static ShieldCapCraft cloneCraft(ShieldCapCraft source) {
        ShieldCapCraft copy = new ShieldCapCraft();
        ShieldCapCraft effective = source == null ? createDefault() : source;
        copy.comment = effective.comment;
        copy.weaponMaxDurability = effective.weaponMaxDurability;
        copy.modCompatibility = effective.modCompatibility;
        copy.modCompatibilityNote = effective.modCompatibilityNote == null
                ? new ArrayList<>()
                : new ArrayList<>(effective.modCompatibilityNote);
        copy.Input = cloneIngredients(effective.Input);
        copy.TimeSeconds = effective.TimeSeconds;
        copy.RequiredMemoriesLevel = effective.RequiredMemoriesLevel;
        copy.BenchRequirements = new ArrayList<>();
        if (effective.BenchRequirements != null) {
            for (ShieldCapCraft.BenchConfig bench : effective.BenchRequirements) {
                copy.BenchRequirements.add(cloneBench(bench));
            }
        }
        copy.Bench = cloneBench(effective.Bench);
        return copy;
    }

    private static List<ShieldCapCraft.BenchConfig> cloneBenchList(List<ShieldCapCraft.BenchConfig> source) {
        List<ShieldCapCraft.BenchConfig> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (ShieldCapCraft.BenchConfig bench : source) {
            copy.add(cloneBench(bench));
        }
        return copy;
    }

    private static ShieldCapCraft.BenchConfig cloneBench(ShieldCapCraft.BenchConfig source) {
        ShieldCapCraft.BenchConfig bench = new ShieldCapCraft.BenchConfig();
        if (source == null) {
            return bench;
        }
        bench.Id = source.Id;
        bench.RequiredTierLevel = source.RequiredTierLevel;
        bench.Categories = source.Categories == null ? new ArrayList<>() : new ArrayList<>(source.Categories);
        return bench;
    }

    private static List<ShieldCapCraft.BenchConfig> getEffectiveBenches(ShieldCapCraft craft) {
        if (craft == null) {
            return new ArrayList<>();
        }
        if (craft.BenchRequirements != null && !craft.BenchRequirements.isEmpty()) {
            return craft.BenchRequirements;
        }
        if (craft.Bench != null) {
            return List.of(craft.Bench);
        }
        return new ArrayList<>();
    }

    private static boolean benchEquals(ShieldCapCraft.BenchConfig left, ShieldCapCraft.BenchConfig right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.Id, right.Id)
                && left.RequiredTierLevel == right.RequiredTierLevel
                && Objects.equals(left.Categories, right.Categories);
    }

    private static ShieldCapCraft.BenchConfig findBench(ShieldCapCraft craft, String id) {
        if (craft == null || id == null) {
            return null;
        }
        ShieldCapCraft.BenchConfig fromList = findBenchInList(craft.BenchRequirements, id);
        if (fromList != null) {
            return fromList;
        }
        if (craft.Bench != null && id.equalsIgnoreCase(craft.Bench.Id)) {
            return craft.Bench;
        }
        return null;
    }

    private static ShieldCapCraft.BenchConfig findBenchInList(List<ShieldCapCraft.BenchConfig> benches, String id) {
        if (benches == null || id == null) {
            return null;
        }
        for (ShieldCapCraft.BenchConfig bench : benches) {
            if (bench != null && bench.Id != null && id.equalsIgnoreCase(bench.Id)) {
                return bench;
            }
        }
        return null;
    }

    private static boolean craftRecipeFieldsEqual(ShieldCapCraft left, ShieldCapCraft right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return ingredientsEqual(left.Input, right.Input)
                && Objects.equals(left.weaponMaxDurability, right.weaponMaxDurability)
                && left.TimeSeconds == right.TimeSeconds
                && left.RequiredMemoriesLevel == right.RequiredMemoriesLevel
                && benchListsEqual(getEffectiveBenches(left), getEffectiveBenches(right))
                && benchEquals(left.Bench, right.Bench);
    }

    private static boolean craftRecipeFieldsChanged(ShieldCapCraft previous, ShieldCapCraft current) {
        return previous != null && current != null && !craftRecipeFieldsEqual(previous, current);
    }

    private static boolean ingredientsEqual(List<ShieldCapCraft.IngredientEntry> left,
                                            List<ShieldCapCraft.IngredientEntry> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            ShieldCapCraft.IngredientEntry leftEntry = left.get(i);
            ShieldCapCraft.IngredientEntry rightEntry = right.get(i);
            if (leftEntry == rightEntry) {
                continue;
            }
            if (leftEntry == null || rightEntry == null) {
                return false;
            }
            if (!Objects.equals(leftEntry.ItemId, rightEntry.ItemId)
                    || leftEntry.Quantity != rightEntry.Quantity) {
                return false;
            }
        }
        return true;
    }

    private static boolean benchListsEqual(List<ShieldCapCraft.BenchConfig> left,
                                           List<ShieldCapCraft.BenchConfig> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!benchEquals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }
}
