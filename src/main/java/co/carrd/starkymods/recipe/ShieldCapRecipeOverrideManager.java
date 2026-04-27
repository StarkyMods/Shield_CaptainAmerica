package co.carrd.starkymods.recipe;

import co.carrd.starkymods.config.ShieldCapCraft;
import co.carrd.starkymods.config.ShieldCapCraftConfigManager;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShieldCapRecipeOverrideManager {
    private static final AtomicBoolean APPLYING_OVERRIDE = new AtomicBoolean(false);

    private ShieldCapRecipeOverrideManager() {
    }

    public static void register(JavaPlugin plugin) {
        plugin.getEventRegistry().register(
                LoadedAssetsEvent.class,
                CraftingRecipe.class,
                ShieldCapRecipeOverrideManager::onRecipeLoad
        );
    }

    private static void onRecipeLoad(
            LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event
    ) {
        if (APPLYING_OVERRIDE.get()) {
            return;
        }

        for (Map.Entry<String, CraftingRecipe> entry : event.getLoadedAssets().entrySet()) {
            if (!isManagedShieldRecipe(entry.getKey())) {
                continue;
            }
            applyConfiguredRecipe();
            break;
        }
    }

    public static boolean applyConfiguredRecipe() {
        if (!APPLYING_OVERRIDE.compareAndSet(false, true)) {
            return false;
        }

        ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
        List<String> recipeIds = ShieldCapCraftConfigManager.getShieldRecipeIds();
        if (config == null || recipeIds == null || recipeIds.isEmpty()) {
            APPLYING_OVERRIDE.set(false);
            return false;
        }

        try {
            List<CraftingRecipe> modifiedRecipes = new java.util.ArrayList<>();
            for (String recipeId : recipeIds) {
                if (recipeId == null || recipeId.isBlank()) {
                    continue;
                }

                CraftingRecipe original = CraftingRecipe.getAssetMap().getAsset(recipeId);
                if (original == null) {
                    System.out.println("[ShieldCap] Could not apply recipe override: recipe not found -> " + recipeId);
                    return false;
                }

                CraftingRecipe modified = buildFromConfig(original, config);
                setRecipeId(modified, recipeId);
                modifiedRecipes.add(modified);
            }

            if (modifiedRecipes.isEmpty()) {
                return false;
            }

            CraftingRecipe.getAssetStore().loadAssets(
                    "StarkyShieldCap:Override",
                    modifiedRecipes
            );
            System.out.println("[ShieldCap] Recipe override applied for " + String.join(", ", recipeIds));
            return true;
        } catch (Exception e) {
            System.out.println("[ShieldCap] Failed to apply recipe override:");
            e.printStackTrace();
            return false;
        } finally {
            APPLYING_OVERRIDE.set(false);
        }
    }

    private static CraftingRecipe buildFromConfig(CraftingRecipe original, ShieldCapCraft config) {
        MaterialQuantity[] inputs = new MaterialQuantity[config.Input.size()];
        for (int i = 0; i < config.Input.size(); i++) {
            ShieldCapCraft.IngredientEntry entry = config.Input.get(i);
            inputs[i] = new MaterialQuantity(entry.ItemId, null, null, entry.Quantity, null);
        }

        MaterialQuantity primaryOutput = original.getPrimaryOutput();

        List<ShieldCapCraft.BenchConfig> configuredBenches = config.BenchRequirements;
        if (configuredBenches == null || configuredBenches.isEmpty()) {
            configuredBenches = config.Bench == null ? Collections.emptyList() : List.of(config.Bench);
        }

        BenchRequirement[] requirements = configuredBenches.stream()
                .filter(Objects::nonNull)
                .map(ShieldCapRecipeOverrideManager::buildBenchRequirement)
                .toArray(BenchRequirement[]::new);

        return new CraftingRecipe(
                inputs,
                primaryOutput,
                original.getOutputs(),
                1,
                requirements,
                config.TimeSeconds,
                original.isKnowledgeRequired(),
                config.RequiredMemoriesLevel
        );
    }

    private static BenchRequirement buildBenchRequirement(ShieldCapCraft.BenchConfig config) {
        BenchRequirement bench = new BenchRequirement();
        bench.type = BenchType.Crafting;
        bench.id = config.Id;
        bench.requiredTierLevel = config.RequiredTierLevel;
        bench.categories = config.Categories == null ? new String[0] : config.Categories.toArray(new String[0]);
        return bench;
    }

    private static void setRecipeId(CraftingRecipe recipe, String id) throws Exception {
        Field field = CraftingRecipe.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(recipe, id);
    }

    private static boolean isManagedShieldRecipe(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return false;
        }
        return ShieldCapCraftConfigManager.getShieldRecipeIds()
                .stream()
                .anyMatch(id -> id.equalsIgnoreCase(recipeId));
    }
}
