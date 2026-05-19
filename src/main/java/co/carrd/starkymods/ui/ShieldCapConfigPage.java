package co.carrd.starkymods.ui;

import co.carrd.starkymods.StarkyShieldCaptainAmerica;
import co.carrd.starkymods.config.ShieldCapConfig;
import co.carrd.starkymods.config.ShieldCapConfigManager;
import co.carrd.starkymods.config.ShieldCapCraft;
import co.carrd.starkymods.config.ShieldCapCraftConfigManager;
import co.carrd.starkymods.config.ShieldCapDamageConfigManager;
import co.carrd.starkymods.config.ShieldCapDamages;
import co.carrd.starkymods.config.ShieldCapDurabilityAssetOverrideManager;
import co.carrd.starkymods.config.ShieldCapDurabilityLiveUpdater;
import co.carrd.starkymods.damage.ShieldCapDamageAssetGenerator;
import co.carrd.starkymods.recipe.ShieldCapRecipeOverrideManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShieldCapConfigPage extends InteractiveCustomUIPage<ShieldCapConfigPage.PageEventData> {
    private static final String SETTINGS_UI_PATH = "Pages/ShieldCapConfigEditor.ui";
    private static final String VIEWER_UI_PATH = "Pages/ShieldCapConfigViewer.ui";
    private static final String HOME_TAB = "home";
    private static final String CONFIG_TAB = "config";
    private static final String CRAFT_TAB = "craft";
    private static final String DAMAGE_TAB = "damage";
    private static final String COMPATIBILITY_TAB = "compatibility";
    private static final String MANIFEST_PATH = "manifest.json";
    private static final int MAX_INGREDIENT_ROWS = 12;
    private static final int MAX_DAMAGE_ROWS = 40;
    private static final String NORMAL_BENCH_ID = "Armor_Bench";
    private static final String NORMAL_BENCH_CATEGORY = "Weapon_Shield";
    private static final String ENDGAME_BENCH_ID = "Endgame_Bench";
    private static final String ENDGAME_BENCH_CATEGORY = "Endgame_Armor_Prisma";

    private static final String HOME_STARKYMODS_URL = "https://starkymods.carrd.co/";
    private static final String HOME_DISCORD_URL = "https://discord.gg/twWWmtnp82";
    private static final String HOME_CURSEFORGE_URL = "https://www.curseforge.com/hytale/mods/starky-shield";
    private static final String HOME_MJOLNIR_URL = "https://www.curseforge.com/hytale/mods/starky-mjolnir";
    private static final String HOME_HY_VOICE_ZOO_URL = "https://www.curseforge.com/hytale/mods/hyvoicezoo";
    private static final String HOME_STASIS_RIFLE_URL = "https://www.curseforge.com/hytale/mods/subnautica-stasis-rifle";
    private static final String HOME_RAYGUN_URL = "https://www.curseforge.com/hytale/mods/starky-raygun";
    private static final String HOME_THUNDERGUN_URL = "https://www.curseforge.com/hytale/mods/starky-thundergun";
    private static final String HOME_FARTGUN_URL = "https://www.curseforge.com/hytale/mods/the-fart-gun";
    private static final String HOME_CUSTOM_SKIN_KEEPER_URL = "https://www.curseforge.com/hytale/mods/custom-skin-keeper";
    private static final String HOME_BLOCKING_ITEMS_URL = "https://www.curseforge.com/hytale/mods/blockingitems";

    private static final String PARTNER_ENDLESS_LEVELING_URL = "https://www.curseforge.com/hytale/mods/endlessleveling";
    private static final String PARTNER_ZEPHYR_URL = "https://www.curseforge.com/hytale/mods/zephyr";
    private static final String PARTNER_PERFECT_PARRIES_URL = "https://www.curseforge.com/hytale/mods/perfect-parries";
    private static final String PARTNER_PERFECT_DODGES_URL = "https://www.curseforge.com/hytale/mods/perfect-dodges";
    private static final String PARTNER_MJOLNIR_URL = HOME_MJOLNIR_URL;

    private static final PluginIdentifier ENDLESS_LEVELING_PLUGIN_ID = new PluginIdentifier("Airijko", "EndlessLevelingCore");
    private static final PluginIdentifier MJOLNIR_PLUGIN_ID = new PluginIdentifier("StarkyMods", "Starky's Mjolnir");
    private static final PluginIdentifier PERFECT_PARRIES_PLUGIN_ID = new PluginIdentifier("narwhals", "Perfect Parries");
    private static final PluginIdentifier ZEPHYR_PLUGIN_ID = new PluginIdentifier("narwhals", "Zephyr");
    private static final PluginIdentifier PERFECT_DODGES_PLUGIN_ID = new PluginIdentifier("narwhals", "Perfect Dodges");
    private static final String SHIELDCAP_VERSION = readShieldCapVersion();

    private final String uiPath;
    private final boolean editable;
    private String activeTab = HOME_TAB;
    private List<ShieldCapCraft.IngredientEntry> craftIngredientDraft;

    private ShieldCapConfigPage(@Nonnull PlayerRef playerRef, String uiPath, boolean editable) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        this.uiPath = uiPath;
        this.editable = editable;
    }

    public static ShieldCapConfigPage createSettingsPage(@Nonnull PlayerRef playerRef) {
        return new ShieldCapConfigPage(playerRef, SETTINGS_UI_PATH, true);
    }

    public static ShieldCapConfigPage createViewerPage(@Nonnull PlayerRef playerRef) {
        return new ShieldCapConfigPage(playerRef, VIEWER_UI_PATH, false);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(uiPath);
        commands.set("#VersionLabel.Text", editable ? SHIELDCAP_VERSION + " Admin" : SHIELDCAP_VERSION);
        commands.set("#TabHomeActive.Visible", HOME_TAB.equals(activeTab));
        commands.set("#TabMainActive.Visible", CONFIG_TAB.equals(activeTab));
        commands.set("#TabCraftActive.Visible", CRAFT_TAB.equals(activeTab));
        commands.set("#TabDamageActive.Visible", DAMAGE_TAB.equals(activeTab));
        commands.set("#TabCompatibilityActive.Visible", COMPATIBILITY_TAB.equals(activeTab));
        commands.set("#HomeTab.Visible", HOME_TAB.equals(activeTab));
        commands.set("#MainTab.Visible", CONFIG_TAB.equals(activeTab));
        commands.set("#CraftTab.Visible", CRAFT_TAB.equals(activeTab));
        commands.set("#DamageTab.Visible", DAMAGE_TAB.equals(activeTab));
        commands.set("#CompatibilityTab.Visible", COMPATIBILITY_TAB.equals(activeTab));

        applyMainConfigToUI(commands);
        refreshCraftConfigForDisplay();
        applyCraftConfigToUI(commands);
        refreshDamageConfigForDisplay();
        applyDamageConfigToUI(commands);
        if (!editable) {
            applyReadOnlyState(commands);
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabHomeButton", buildEventData("tab:" + HOME_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabMainButton", buildEventData("tab:" + CONFIG_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabCraftButton", buildEventData("tab:" + CRAFT_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDamageButton", buildEventData("tab:" + DAMAGE_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabCompatibilityButton", buildEventData("tab:" + COMPATIBILITY_TAB));
        bind(events, "#CloseUIButton", "close");

        bind(events, "#HomeWebsiteButton", "home:link:starkymods");
        bind(events, "#HomeDiscordButton", "home:link:discord");
        bind(events, "#HomeCurseforgeButton", "home:link:curseforge");
        bind(events, "#HomeCard1", "home:link:mjolnir");
        bind(events, "#HomeCard2", "home:link:hyvoicezoo");
        bind(events, "#HomeCard3", "home:link:stasisrifle");
        bind(events, "#HomeCard4", "home:link:raygun");
        bind(events, "#HomeCard5", "home:link:thundergun");
        bind(events, "#HomeCard6", "home:link:fartgun");
        bind(events, "#HomeCard7", "home:link:customskinkeeper");
        bind(events, "#HomeCard8", "home:link:blockingitems");

        bind(events, "#CompatibilityShieldCard", "compatibility:partner:mjolnir");
        bind(events, "#CompatibilityShieldMenuButton", "compatibility:menu:mjolnir");
        bind(events, "#CompatibilityPartnerCard", "compatibility:partner:endlessleveling");
        bind(events, "#CompatibilityPartnerMenuButton", "compatibility:menu:endlessleveling");
        bind(events, "#CompatibilityCard1", "compatibility:partner:zephyr");
        bind(events, "#CompatibilityCard1MenuButton", "compatibility:menu:zephyr");
        bind(events, "#CompatibilityCard2", "compatibility:partner:perfectparries");
        bind(events, "#CompatibilityCard2MenuButton", "compatibility:menu:perfectparries");
        bind(events, "#CompatibilityCard3", "compatibility:partner:perfectdodges");
        bind(events, "#CompatibilityCard3MenuButton", "compatibility:menu:perfectdodges");

        bindConfigButtons(events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }
        String action = data.action.trim();
        if (action.startsWith("tab:")) {
            if (CRAFT_TAB.equals(activeTab)) {
                syncIngredientDraftFromData(data);
            }
            String tab = action.substring("tab:".length()).trim();
            if (!tab.isBlank()) {
                if (CRAFT_TAB.equals(tab) && !CRAFT_TAB.equals(activeTab)) {
                    craftIngredientDraft = null;
                }
                activeTab = tab;
                rebuild();
            }
            return;
        }
        if ("close".equals(action)) {
            close();
            return;
        }
        if (action.startsWith("home:link:")) {
            handleHomeLinkAction(action);
            rebuild();
            return;
        }
        if (action.startsWith("compatibility:")) {
            handleCompatibilityAction(ref, store, action);
            if (!action.startsWith("compatibility:menu:")) {
                rebuild();
            }
            return;
        }
        if (action.startsWith("apply:")) {
            String tab = action.substring("apply:".length()).trim();
            if (CONFIG_TAB.equals(tab) && editable) {
                applyMainConfig(store, ref, data);
                sendMessage("Config saved. Live reload applied.");
                rebuild();
                return;
            }
            if (CRAFT_TAB.equals(tab) && editable) {
                applyCraftConfig(data);
                sendMessage("Crafting saved. Live reload applied.");
                rebuild();
                return;
            }
            if (DAMAGE_TAB.equals(tab) && editable) {
                applyDamageConfig(data);
                sendMessage("Damages saved. Live reload applied.");
                rebuild();
                return;
            }
            sendMessage("This SHIELDCAP UI tab is not connected yet.");
            rebuild();
            return;
        }
        if (action.startsWith("reset:")) {
            String tab = action.substring("reset:".length()).trim();
            if (CONFIG_TAB.equals(tab) && editable) {
                ShieldCapConfigManager.resetMainConfigToDefaults();
                refreshPlayerVisuals(store, ref);
                sendMessage("Config reset. Live reload applied.");
                rebuild();
                return;
            }
            if (CRAFT_TAB.equals(tab) && editable) {
                StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
                if (plugin != null) {
                    plugin.markInternalCraftConfigWrite();
                }
                ShieldCapCraft defaults = ShieldCapCraftConfigManager.createDefaultCraftConfigSnapshot();
                defaults.modCompatibility = data.craftModCompatibility;
                ShieldCapCraftConfigManager.saveCraftState(defaults, false);
                if (data.craftModCompatibility && isEndgameQoLActive()) {
                    ShieldCapCraftConfigManager.applyEndgameCraftCompatibilityProfile();
                }
                craftIngredientDraft = cloneIngredients(ShieldCapCraftConfigManager.getConfig().Input);
                applyCraftHotReloadAsync();
                sendMessage("Crafting reset. Live reload applied.");
                rebuild();
                return;
            }
            if (DAMAGE_TAB.equals(tab) && editable) {
                StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
                if (plugin != null) {
                    plugin.markInternalDamageConfigWrite();
                }
                ShieldCapDamages defaults = data.damageModCompatibility && plugin != null
                        ? ShieldCapDamageConfigManager.createDamageConfigSnapshotForProfile(plugin.getActiveDamageCompatibilityProfile())
                        : ShieldCapDamageConfigManager.createDefault();
                defaults.modCompatibility = data.damageModCompatibility;
                ShieldCapDamageConfigManager.saveDamagesFromUi(defaults, false);
                applyDamageHotReloadAsync();
                sendMessage("Damages reset. Live reload applied.");
                rebuild();
                return;
            }
            sendMessage("This SHIELDCAP UI tab is not connected yet.");
            rebuild();
            return;
        }
        if (action.startsWith("ingredient:")) {
            if (editable) {
                syncIngredientDraftFromData(data);
                handleIngredientAction(action);
            }
            rebuild();
            return;
        }
    }

    private void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, buildEventData(action));
    }

    private EventData buildEventData(String action) {
        if (!editable) {
            return new EventData().append("Action", action);
        }
        EventData data = new EventData()
                .append("Action", action)
                .append("@allowShieldCraft", "#AllowCraftingToggle.Value")
                .append("@shieldKeepsSignatureEnergyWhenSwapped", "#KeepSignatureEnergyToggle.Value")
                .append("@visualShieldInPlayersBack", "#VisualShieldInPlayersBackToggle.Value")
                .append("@fallResistanceWhenBlocking", "#FallResistanceWhenBlockingToggle.Value")
                .append("@damageFromTheBackResistance", "#DamageFromBackResistanceToggle.Value")
                .append("@doubleJump", "#DoubleJumpToggle.Value")
                .append("@fallStarAttack", "#FallStarAttackToggle.Value")
                .append("@guardBash", "#GuardBashToggle.Value")
                .append("@guardBashPushLaunch", "#GuardBashPushLaunchToggle.Value")
                .append("@sprintAttack", "#SprintAttackToggle.Value")
                .append("@kickPushAttack", "#KickPushAttackToggle.Value")
                .append("@throwAttack", "#ThrowToggle.Value")
                .append("@throwLeftHand", "#ThrowLeftHandToggle.Value")
                .append("@furiousOnslaught", "#FuriousOnslaughtToggle.Value")
                .append("@guardBashShockwaveWithMjolnir", "#GuardBashShockwaveMjolnirToggle.Value")
                .append("@weaponMaxDurability", "#WeaponMaxDurabilityInput.Value")
                .append("@craftModCompatibility", "#CraftModCompatibilityToggle.Value")
                .append("@craftTimeSeconds", "#CraftTimeSecondsInput.Value")
                .append("@craftBenchTier", "#CraftBenchTierInput.Value")
                .append("@craftEndgameBenchTier", "#CraftEndgameBenchTierInput.Value")
                .append("@craftRequiredMemoriesLevel", "#CraftRequiredMemoriesInput.Value")
                .append("@damageModCompatibility", "#DamageModCompatibilityToggle.Value");
        for (int i = 1; i <= MAX_INGREDIENT_ROWS; i++) {
            data.append("@ingredientId" + i, "#IngredientId" + i + ".Value");
            data.append("@ingredientQty" + i, "#IngredientQuantity" + i + ".Value");
        }
        for (int i = 1; i <= MAX_DAMAGE_ROWS; i++) {
            data.append("@damageValue" + i, "#DamageValue" + i + ".Value");
        }
        return data;
    }

    private void bindConfigButtons(UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveMainButton",
                buildEventData("apply:" + CONFIG_TAB)
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetMainButton", buildEventData("reset:" + CONFIG_TAB));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveCraftButton",
                buildEventData("apply:" + CRAFT_TAB)
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveDamageButton", buildEventData("apply:" + DAMAGE_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetCraftButton", buildEventData("reset:" + CRAFT_TAB));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetDamageButton", buildEventData("reset:" + DAMAGE_TAB));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AddIngredientButton",
                buildEventData("ingredient:add")
        );
        for (int i = 1; i <= MAX_INGREDIENT_ROWS; i++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#IngredientRemove" + i,
                    buildEventData("ingredient:remove:" + i)
            );
        }
    }

    private void applyMainConfigToUI(UICommandBuilder commands) {
        ShieldCapConfig config = ShieldCapConfigManager.getMainConfigSnapshot();
        commands.set("#AllowCraftingToggle.Value", !Boolean.FALSE.equals(config.allowShieldCraft));
        commands.set(
                "#KeepSignatureEnergyToggle.Value",
                !Boolean.FALSE.equals(config.shieldKeepsSignatureEnergyWhenSwapped)
        );
        commands.set("#VisualShieldInPlayersBackToggle.Value", !Boolean.FALSE.equals(config.visualShieldInPlayersBack));
        commands.set("#FallResistanceWhenBlockingToggle.Value", !Boolean.FALSE.equals(config.fallResistanceWhenBlocking));
        commands.set("#DamageFromBackResistanceToggle.Value", !Boolean.FALSE.equals(config.damageFromTheBackResistance));
        commands.set("#DoubleJumpToggle.Value", !Boolean.FALSE.equals(config.doubleJump));
        commands.set("#FallStarAttackToggle.Value", !Boolean.FALSE.equals(config.fallStarAttack));
        commands.set("#GuardBashToggle.Value", !Boolean.FALSE.equals(config.guardBash));
        commands.set("#GuardBashPushLaunchToggle.Value", !Boolean.FALSE.equals(config.guardBashPushLaunch));
        commands.set("#SprintAttackToggle.Value", !Boolean.FALSE.equals(config.sprintAttack));
        commands.set("#KickPushAttackToggle.Value", !Boolean.FALSE.equals(config.kickPushAttack));
        commands.set("#ThrowToggle.Value", !Boolean.FALSE.equals(config.throwAttack));
        commands.set("#ThrowLeftHandToggle.Value", !Boolean.FALSE.equals(config.throwLeftHand));
        commands.set("#FuriousOnslaughtToggle.Value", !Boolean.FALSE.equals(config.furiousOnslaught));
        commands.set(
                "#GuardBashShockwaveMjolnirToggle.Value",
                !Boolean.FALSE.equals(config.guardBashShockwaveWithMjolnir)
        );
    }

    private void applyCraftConfigToUI(UICommandBuilder commands) {
        ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
        if (config == null) {
            config = new ShieldCapCraft();
        }

        if (editable) {
            commands.set("#CraftCommentText.Text", safeText(config.comment));
            commands.set("#CraftModCompatibilityToggle.Value", config.modCompatibility == null || config.modCompatibility);
        }
        setNumericField(commands, "#WeaponMaxDurabilityInput", numberOrZero(config.weaponMaxDurability));
        setNumericField(commands, "#CraftTimeSecondsInput", Math.max(0, config.TimeSeconds));
        setNumericField(commands, "#CraftBenchTierInput", resolveBenchTier(config));
        boolean hasEndgameBench = hasBench(config, ENDGAME_BENCH_ID);
        commands.set("#CraftEndgameBenchTierRow.Visible", hasEndgameBench);
        setNumericField(commands, "#CraftEndgameBenchTierInput", hasEndgameBench ? resolveEndgameBenchTier(config) : 0);
        setNumericField(commands, "#CraftRequiredMemoriesInput", Math.max(0, config.RequiredMemoriesLevel));
        if (editable) {
            commands.set("#CraftConfigNote.Text", joinLines(config.modCompatibilityNote));
        }

        List<ShieldCapCraft.IngredientEntry> ingredients = getIngredientDraft(config);
        for (int i = 0; i < MAX_INGREDIENT_ROWS; i++) {
            int index = i + 1;
            commands.set("#IngredientRow" + index + ".Visible", false);
            setTextField(commands, "#IngredientId" + index, "");
            setNumericField(commands, "#IngredientQuantity" + index, 0);
        }
        int ingredientCount = Math.min(MAX_INGREDIENT_ROWS, ingredients.size());
        for (int i = 0; i < ingredientCount; i++) {
            int index = i + 1;
            ShieldCapCraft.IngredientEntry entry = ingredients.get(i);
            commands.set("#IngredientRow" + index + ".Visible", true);
            setTextField(commands, "#IngredientId" + index, entry == null ? "" : entry.ItemId);
            setNumericField(commands, "#IngredientQuantity" + index, entry == null ? 0 : Math.max(0, entry.Quantity));
        }
    }

    private void applyDamageConfigToUI(UICommandBuilder commands) {
        ShieldCapDamages config = ShieldCapDamageConfigManager.getConfig();
        List<ShieldCapDamageConfigManager.DamageDefinition> rows = damageRows();
        for (int i = 1; i <= MAX_DAMAGE_ROWS; i++) {
            commands.set("#DamageRow" + i + ".Visible", false);
            commands.set("#DamageLabel" + i + ".Text", "");
            setNumericField(commands, "#DamageValue" + i, 0);
        }

        int rowCount = Math.min(MAX_DAMAGE_ROWS, rows.size());
        for (int i = 0; i < rowCount; i++) {
            int row = i + 1;
            ShieldCapDamageConfigManager.DamageDefinition definition = rows.get(i);
            boolean launchForce = ShieldCapDamageConfigManager.getLaunchForceDefinitions().contains(definition);
            commands.set("#DamageRow" + row + ".Visible", true);
            commands.set("#DamageLabel" + row + ".Text", definition.displayKey());
            double value = launchForce
                    ? config.getLaunchForce(definition.displayKey())
                    : config.getDamage(definition.displayKey());
            setNumericField(commands, "#DamageValue" + row, value);
        }
        if (editable) {
            commands.set("#DamageModCompatibilityToggle.Value", config.modCompatibility == null || config.modCompatibility);
            commands.set("#DamageConfigNote.Text", joinLines(config.modCompatibilityNote));
        }

    }

    private void refreshCraftConfigForDisplay() {
        if (editable && craftIngredientDraft != null) {
            return;
        }
        ShieldCapCraftConfigManager.reloadCraftIfValid();
    }

    private void refreshDamageConfigForDisplay() {
        ShieldCapDamageConfigManager.reloadDamagesIfValid();
    }

    private void applyReadOnlyState(UICommandBuilder commands) {
        commands.set("#AllowCraftingToggle.Disabled", true);
        commands.set("#KeepSignatureEnergyToggle.Disabled", true);
        commands.set("#VisualShieldInPlayersBackToggle.Disabled", true);
        commands.set("#FallResistanceWhenBlockingToggle.Disabled", true);
        commands.set("#DamageFromBackResistanceToggle.Disabled", true);
        commands.set("#DoubleJumpToggle.Disabled", true);
        commands.set("#FallStarAttackToggle.Disabled", true);
        commands.set("#GuardBashToggle.Disabled", true);
        commands.set("#GuardBashPushLaunchToggle.Disabled", true);
        commands.set("#SprintAttackToggle.Disabled", true);
        commands.set("#KickPushAttackToggle.Disabled", true);
        commands.set("#ThrowToggle.Disabled", true);
        commands.set("#ThrowLeftHandToggle.Disabled", true);
        commands.set("#FuriousOnslaughtToggle.Disabled", true);
        commands.set("#GuardBashShockwaveMjolnirToggle.Disabled", true);
        commands.set("#SaveMainButton.Visible", false);
        commands.set("#SaveMainButton.Disabled", true);
        commands.set("#ResetMainButton.Visible", false);
        commands.set("#ResetMainButton.Disabled", true);
        commands.set("#SaveCraftButton.Visible", false);
        commands.set("#SaveCraftButton.Disabled", true);
        commands.set("#ResetCraftButton.Visible", false);
        commands.set("#ResetCraftButton.Disabled", true);
        commands.set("#SaveDamageButton.Visible", false);
        commands.set("#SaveDamageButton.Disabled", true);
        commands.set("#ResetDamageButton.Visible", false);
        commands.set("#ResetDamageButton.Disabled", true);
        commands.set("#AddIngredientButton.Visible", false);
        commands.set("#AddIngredientButton.Disabled", true);
        for (int i = 1; i <= MAX_INGREDIENT_ROWS; i++) {
            hideElement(commands, "#IngredientRemove" + i);
            commands.set("#IngredientRemove" + i + ".Disabled", true);
        }
    }

    private void applyMainConfig(Store<EntityStore> store, Ref<EntityStore> ref, PageEventData data) {
        ShieldCapConfig config = new ShieldCapConfig();
        config.allowShieldCraft = data.allowShieldCraft;
        config.shieldKeepsSignatureEnergyWhenSwapped = data.shieldKeepsSignatureEnergyWhenSwapped;
        config.visualShieldInPlayersBack = data.visualShieldInPlayersBack;
        config.fallResistanceWhenBlocking = data.fallResistanceWhenBlocking;
        config.damageFromTheBackResistance = data.damageFromTheBackResistance;
        config.doubleJump = data.doubleJump;
        config.fallStarAttack = data.fallStarAttack;
        config.guardBash = data.guardBash;
        config.guardBashPushLaunch = data.guardBashPushLaunch;
        config.sprintAttack = data.sprintAttack;
        config.kickPushAttack = data.kickPushAttack;
        config.throwAttack = data.throwAttack;
        config.throwLeftHand = data.throwLeftHand;
        config.furiousOnslaught = data.furiousOnslaught;
        config.guardBashShockwaveWithMjolnir = data.guardBashShockwaveWithMjolnir;
        ShieldCapConfigManager.saveMainConfigState(config);
        refreshPlayerVisuals(store, ref);
    }

    private void applyCraftConfig(PageEventData data) {
        ShieldCapCraft config = ShieldCapCraftConfigManager.getConfigSnapshot();
        if (config == null) {
            config = new ShieldCapCraft();
        }

        config.weaponMaxDurability = clampInt(data.weaponMaxDurability, 0);
        config.modCompatibility = data.craftModCompatibility;
        config.TimeSeconds = clampInt(data.craftTimeSeconds, 1);
        config.RequiredMemoriesLevel = clampInt(data.craftRequiredMemoriesLevel, 0);
        boolean hadEndgameBench = hasBench(config, ENDGAME_BENCH_ID);
        applyBenchTier(config, NORMAL_BENCH_ID, NORMAL_BENCH_CATEGORY, clampInt(data.craftBenchTier, 0), true);
        if (hadEndgameBench) {
            applyBenchTier(config, ENDGAME_BENCH_ID, ENDGAME_BENCH_CATEGORY, clampInt(data.craftEndgameBenchTier, 0), false);
        }
        syncIngredientDraftFromData(data);
        config.Input = buildIngredientsFromDraft();

        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin != null) {
            plugin.markInternalCraftConfigWrite();
        }
        ShieldCapCraftConfigManager.saveCraftState(config);
        craftIngredientDraft = cloneIngredients(ShieldCapCraftConfigManager.getConfig().Input);
        applyCraftHotReloadAsync();
    }

    private void applyDamageConfig(PageEventData data) {
        ShieldCapDamages config = ShieldCapDamageConfigManager.getConfigSnapshot();
        if (config == null) {
            config = new ShieldCapDamages();
        }
        config.modCompatibility = data.damageModCompatibility;
        config.legacyDisableCompatibilityProfiles = null;
        config.damageValues = new LinkedHashMap<>();
        config.launchForces = new LinkedHashMap<>();

        List<ShieldCapDamageConfigManager.DamageDefinition> rows = damageRows();
        for (int i = 0; i < Math.min(rows.size(), MAX_DAMAGE_ROWS); i++) {
            ShieldCapDamageConfigManager.DamageDefinition definition = rows.get(i);
            double value = Math.max(0.0, data.damageValues[i]);
            if (ShieldCapDamageConfigManager.getLaunchForceDefinitions().contains(definition)) {
                config.launchForces.put(definition.displayKey(), value);
            } else {
                config.damageValues.put(definition.displayKey(), value);
            }
        }

        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (plugin != null) {
            plugin.markInternalDamageConfigWrite();
        }
        ShieldCapDamageConfigManager.saveDamagesFromUi(config);
        applyDamageHotReloadAsync();
    }

    private void applyDamageHotReloadAsync() {
        runAsync("ShieldCapDamageApply", ShieldCapDamageAssetGenerator::generateAndReload);
    }

    private List<ShieldCapDamageConfigManager.DamageDefinition> damageRows() {
        return ShieldCapDamageConfigManager.getAllDefinitions();
    }

    private void applyCraftHotReloadAsync() {
        runAsync("ShieldCapCraftApply", this::applyCraftHotReload);
    }

    private void applyCraftHotReload() {
        try {
            ShieldCapRecipeOverrideManager.applyConfiguredRecipe();
            ShieldCapDurabilityAssetOverrideManager.applyConfiguredDurabilityAssets();
            ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
            ShieldCapDurabilityLiveUpdater.applyEverywhereLoaded(config == null ? null : config.weaponMaxDurability);
        } catch (Exception e) {
            sendMessage("Live reload failed: " + e.getMessage());
        }
    }

    private void runAsync(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void applyBenchTier(ShieldCapCraft config, String benchId, String category, int tier, boolean makePrimary) {
        if (config == null) {
            return;
        }

        List<ShieldCapCraft.BenchConfig> benches = config.BenchRequirements == null
                ? new ArrayList<>()
                : new ArrayList<>(config.BenchRequirements);
        ShieldCapCraft.BenchConfig target = findBenchInList(benches, benchId);
        if (target == null) {
            target = createBench(benchId, category);
            benches.add(target);
        }

        target.RequiredTierLevel = Math.max(0, tier);
        config.BenchRequirements = benches;
        if (makePrimary) {
            config.Bench = copyBench(target);
        }
    }

    private int resolveBenchTier(ShieldCapCraft config) {
        ShieldCapCraft.BenchConfig bench = findBench(config, NORMAL_BENCH_ID);
        if (bench != null) {
            return Math.max(0, bench.RequiredTierLevel);
        }
        if (config == null) {
            return 0;
        }
        if (config.BenchRequirements != null && !config.BenchRequirements.isEmpty()
                && config.BenchRequirements.get(0) != null) {
            return Math.max(0, config.BenchRequirements.get(0).RequiredTierLevel);
        }
        if (config.Bench != null) {
            return Math.max(0, config.Bench.RequiredTierLevel);
        }
        return 0;
    }

    private int resolveEndgameBenchTier(ShieldCapCraft config) {
        ShieldCapCraft.BenchConfig bench = findBench(config, ENDGAME_BENCH_ID);
        return bench == null ? 0 : Math.max(0, bench.RequiredTierLevel);
    }

    private ShieldCapCraft.BenchConfig copyBench(ShieldCapCraft.BenchConfig source) {
        ShieldCapCraft.BenchConfig copy = new ShieldCapCraft.BenchConfig();
        if (source == null) {
            return copy;
        }
        copy.Id = source.Id;
        copy.RequiredTierLevel = source.RequiredTierLevel;
        copy.Categories = source.Categories == null ? new ArrayList<>() : new ArrayList<>(source.Categories);
        return copy;
    }

    private ShieldCapCraft.BenchConfig createBench(String id, String category) {
        ShieldCapCraft.BenchConfig bench = new ShieldCapCraft.BenchConfig();
        bench.Id = id;
        bench.Categories = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            bench.Categories.add(category);
        }
        return bench;
    }

    private boolean hasBench(ShieldCapCraft config, String id) {
        return findBench(config, id) != null;
    }

    private ShieldCapCraft.BenchConfig findBench(ShieldCapCraft config, String id) {
        if (config == null || id == null) {
            return null;
        }
        ShieldCapCraft.BenchConfig fromList = findBenchInList(config.BenchRequirements, id);
        if (fromList != null) {
            return fromList;
        }
        if (config.Bench != null && config.Bench.Id != null && id.equalsIgnoreCase(config.Bench.Id)) {
            return config.Bench;
        }
        return null;
    }

    private ShieldCapCraft.BenchConfig findBenchInList(List<ShieldCapCraft.BenchConfig> benches, String id) {
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

    private void refreshPlayerVisuals(Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store == null || ref == null || !ref.isValid()
                ? null
                : store.getComponent(ref, Player.getComponentType());
        StarkyShieldCaptainAmerica plugin = StarkyShieldCaptainAmerica.getInstance();
        if (player != null && plugin != null) {
            plugin.getVisualSyncService().syncDeferred(player, true);
        }
    }

    private void handleHomeLinkAction(String action) {
        String target = action.substring("home:link:".length()).trim();
        switch (target.toLowerCase()) {
            case "starkymods" -> sendMessage(Message.raw(HOME_STARKYMODS_URL).link(HOME_STARKYMODS_URL));
            case "discord" -> sendMessage(Message.raw(HOME_DISCORD_URL).link(HOME_DISCORD_URL));
            case "curseforge" -> sendMessage(Message.raw(HOME_CURSEFORGE_URL).link(HOME_CURSEFORGE_URL));
            case "mjolnir" -> sendMessage(Message.raw(HOME_MJOLNIR_URL).link(HOME_MJOLNIR_URL));
            case "hyvoicezoo" -> sendMessage(Message.raw(HOME_HY_VOICE_ZOO_URL).link(HOME_HY_VOICE_ZOO_URL));
            case "stasisrifle" -> sendMessage(Message.raw(HOME_STASIS_RIFLE_URL).link(HOME_STASIS_RIFLE_URL));
            case "raygun" -> sendMessage(Message.raw(HOME_RAYGUN_URL).link(HOME_RAYGUN_URL));
            case "thundergun" -> sendMessage(Message.raw(HOME_THUNDERGUN_URL).link(HOME_THUNDERGUN_URL));
            case "fartgun" -> sendMessage(Message.raw(HOME_FARTGUN_URL).link(HOME_FARTGUN_URL));
            case "customskinkeeper" -> sendMessage(Message.raw(HOME_CUSTOM_SKIN_KEEPER_URL).link(HOME_CUSTOM_SKIN_KEEPER_URL));
            case "blockingitems" -> sendMessage(Message.raw(HOME_BLOCKING_ITEMS_URL).link(HOME_BLOCKING_ITEMS_URL));
            default -> {
            }
        }
    }

    private void handleCompatibilityAction(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        if (action.startsWith("compatibility:partner:")) {
            String target = action.substring("compatibility:partner:".length()).trim();
            switch (target.toLowerCase()) {
                case "endlessleveling" -> sendMessage(Message.raw(PARTNER_ENDLESS_LEVELING_URL).link(PARTNER_ENDLESS_LEVELING_URL));
                case "zephyr" -> sendMessage(Message.raw(PARTNER_ZEPHYR_URL).link(PARTNER_ZEPHYR_URL));
                case "perfectparries" -> sendMessage(Message.raw(PARTNER_PERFECT_PARRIES_URL).link(PARTNER_PERFECT_PARRIES_URL));
                case "perfectdodges" -> sendMessage(Message.raw(PARTNER_PERFECT_DODGES_URL).link(PARTNER_PERFECT_DODGES_URL));
                case "mjolnir" -> sendMessage(Message.raw(PARTNER_MJOLNIR_URL).link(PARTNER_MJOLNIR_URL));
                default -> {
                }
            }
            return;
        }
        if (!action.startsWith("compatibility:menu:")) {
            return;
        }
        String target = action.substring("compatibility:menu:".length()).trim();
        boolean isOp = PermissionsModule.get().hasPermission(playerRef.getUuid(), "*");
        switch (target.toLowerCase()) {
            case "endlessleveling" -> runCompatibilityMenuCommand(ref, store, ENDLESS_LEVELING_PLUGIN_ID, "el", "Endless Leveling mod is not active", PARTNER_ENDLESS_LEVELING_URL);
            case "mjolnir" -> runCompatibilityMenuCommand(ref, store, MJOLNIR_PLUGIN_ID, "mjolnir", "Starky's Mjolnir mod is not active", PARTNER_MJOLNIR_URL);
            case "perfectparries" -> runCompatibilityMenuCommand(ref, store, PERFECT_PARRIES_PLUGIN_ID, isOp ? "parrymodsettings" : "parrymod", "Perfect Parries mod is not active", PARTNER_PERFECT_PARRIES_URL);
            case "zephyr" -> runCompatibilityMenuCommand(ref, store, ZEPHYR_PLUGIN_ID, isOp ? "zephyr settings" : "zephyr", "Zephyr mod is not active", PARTNER_ZEPHYR_URL);
            case "perfectdodges" -> runCompatibilityMenuCommand(ref, store, PERFECT_DODGES_PLUGIN_ID, isOp ? "pd admin" : "pd", "Perfect Dodges mod is not active", PARTNER_PERFECT_DODGES_URL);
            default -> {
            }
        }
    }

    private void runCompatibilityMenuCommand(Ref<EntityStore> ref, Store<EntityStore> store,
                                             PluginIdentifier pluginId, String command, String inactiveMessage, String curseForgeUrl) {
        if (!isPluginActive(pluginId)) {
            sendMessage(Message.raw(inactiveMessage + ". Open CurseForge page").link(curseForgeUrl));
            return;
        }
        executePlayerCommand(command);
    }

    private boolean isPluginActive(PluginIdentifier pluginId) {
        PluginManager pluginManager = PluginManager.get();
        return pluginManager != null && pluginManager.getPlugin(pluginId) != null;
    }

    private boolean isEndgameQoLActive() {
        PluginManager pluginManager = PluginManager.get();
        return pluginManager != null
                && pluginManager.getPlugin(new PluginIdentifier("Config", "Endgame&QoL")) != null;
    }

    private void executePlayerCommand(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String normalizedCommand = command.startsWith("/") ? command.substring(1) : command;
        Player player = playerRef == null ? null : playerRef.getComponent(Player.getComponentType());
        if (player != null) {
            CommandManager.get().handleCommand(player, normalizedCommand);
            return;
        }
        CommandManager.get().handleCommand(playerRef, normalizedCommand);
    }

    private List<ShieldCapCraft.IngredientEntry> getIngredientDraft(ShieldCapCraft config) {
        if (!editable) {
            return cloneIngredients(config == null ? null : config.Input);
        }
        if (craftIngredientDraft != null) {
            return craftIngredientDraft;
        }
        craftIngredientDraft = cloneIngredients(config == null ? null : config.Input);
        return craftIngredientDraft;
    }

    private void syncIngredientDraftFromData(PageEventData data) {
        if (!editable || data == null) {
            return;
        }
        if (craftIngredientDraft == null) {
            ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
            craftIngredientDraft = cloneIngredients(config == null ? null : config.Input);
        }
        int count = Math.min(craftIngredientDraft.size(), MAX_INGREDIENT_ROWS);
        for (int i = 0; i < count; i++) {
            ShieldCapCraft.IngredientEntry entry = craftIngredientDraft.get(i);
            if (entry == null) {
                entry = new ShieldCapCraft.IngredientEntry();
                craftIngredientDraft.set(i, entry);
            }
            entry.ItemId = normalizeItemId(data.ingredientIds[i]);
            entry.Quantity = clampInt(data.ingredientQuantities[i], 1);
        }
    }

    private void handleIngredientAction(String action) {
        if ("ingredient:add".equals(action)) {
            addIngredientRow();
            return;
        }
        if (action.startsWith("ingredient:remove:")) {
            String indexValue = action.substring("ingredient:remove:".length()).trim();
            try {
                removeIngredientRow(Integer.parseInt(indexValue) - 1);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void addIngredientRow() {
        if (craftIngredientDraft == null) {
            ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
            craftIngredientDraft = cloneIngredients(config == null ? null : config.Input);
        }
        if (craftIngredientDraft.size() >= MAX_INGREDIENT_ROWS) {
            sendMessage("Ingredient limit reached.");
            return;
        }
        craftIngredientDraft.add(new ShieldCapCraft.IngredientEntry("", 1));
    }

    private void removeIngredientRow(int index) {
        if (craftIngredientDraft == null) {
            ShieldCapCraft config = ShieldCapCraftConfigManager.getConfig();
            craftIngredientDraft = cloneIngredients(config == null ? null : config.Input);
        }
        if (index < 0 || index >= craftIngredientDraft.size()) {
            return;
        }
        craftIngredientDraft.remove(index);
    }

    private List<ShieldCapCraft.IngredientEntry> buildIngredientsFromDraft() {
        List<ShieldCapCraft.IngredientEntry> ingredients = new ArrayList<>();
        if (craftIngredientDraft == null) {
            return ingredients;
        }
        int count = Math.min(craftIngredientDraft.size(), MAX_INGREDIENT_ROWS);
        for (int i = 0; i < count; i++) {
            ShieldCapCraft.IngredientEntry entry = craftIngredientDraft.get(i);
            if (entry == null) {
                continue;
            }
            String itemId = normalizeItemId(entry.ItemId);
            if (itemId == null) {
                continue;
            }
            ingredients.add(new ShieldCapCraft.IngredientEntry(itemId, Math.max(1, entry.Quantity)));
        }
        return ingredients;
    }

    private List<ShieldCapCraft.IngredientEntry> cloneIngredients(List<ShieldCapCraft.IngredientEntry> source) {
        List<ShieldCapCraft.IngredientEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (ShieldCapCraft.IngredientEntry entry : source) {
            if (entry == null) {
                copy.add(new ShieldCapCraft.IngredientEntry("", 1));
                continue;
            }
            copy.add(new ShieldCapCraft.IngredientEntry(entry.ItemId, entry.Quantity));
        }
        return copy;
    }

    private String normalizeItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        String trimmed = itemId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void hideElement(UICommandBuilder commands, String selector) {
        commands.set(selector + ".Visible", false);
    }

    private void setTextField(UICommandBuilder commands, String selector, String value) {
        String text = safeText(value);
        if (editable) {
            commands.set(selector + ".Value", text);
            return;
        }
        commands.set(selector + ".Text", text);
    }

    private void setNumericField(UICommandBuilder commands, String selector, double value) {
        if (editable) {
            commands.set(selector + ".Value", value);
            return;
        }
        commands.set(selector + ".Text", formatDisplayNumber(value));
    }

    private int numberOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int clampInt(double value, int min) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, (int) Math.round(value));
    }

    private String formatDisplayNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        double rounded = Math.round(value * 10.0) / 10.0;
        long whole = Math.round(rounded);
        if (Math.abs(rounded - whole) < 0.000001) {
            return Long.toString(whole);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private void sendMessage(String text) {
        sendMessage(Message.raw(text));
    }

    private void sendMessage(Message message) {
        PlayerRef player = playerRef;
        if (player != null && player.isValid() && message != null) {
            player.sendMessage(message);
        }
    }

    private static String readShieldCapVersion() {
        String version = readVersionFromResource(MANIFEST_PATH);
        if (version != null) {
            return version;
        }
        version = readVersionFromResource("/" + MANIFEST_PATH);
        if (version != null) {
            return version;
        }
        version = readVersionFromFile(new File("src/main/resources/" + MANIFEST_PATH));
        return version == null ? "?" : version;
    }

    private static String readVersionFromResource(String path) {
        try (InputStream stream = ShieldCapConfigPage.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            return readVersionFromStream(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readVersionFromFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                return null;
            }
            try (InputStream stream = Files.newInputStream(file.toPath())) {
                return readVersionFromStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readVersionFromStream(InputStream stream) {
        try {
            if (stream == null) {
                return null;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("Version")) {
                String version = root.get("Version").getAsString();
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = buildCodec();

        public String action;
        public boolean allowShieldCraft;
        public boolean shieldKeepsSignatureEnergyWhenSwapped;
        public boolean visualShieldInPlayersBack;
        public boolean fallResistanceWhenBlocking;
        public boolean damageFromTheBackResistance;
        public boolean doubleJump;
        public boolean fallStarAttack;
        public boolean guardBash;
        public boolean guardBashPushLaunch;
        public boolean sprintAttack;
        public boolean kickPushAttack;
        public boolean throwAttack;
        public boolean throwLeftHand;
        public boolean furiousOnslaught;
        public boolean guardBashShockwaveWithMjolnir;
        public double weaponMaxDurability;
        public boolean craftModCompatibility;
        public double craftTimeSeconds;
        public double craftBenchTier;
        public double craftEndgameBenchTier;
        public double craftRequiredMemoriesLevel;
        public boolean damageModCompatibility;
        public final String[] ingredientIds = new String[MAX_INGREDIENT_ROWS];
        public final double[] ingredientQuantities = new double[MAX_INGREDIENT_ROWS];
        public final double[] damageValues = new double[MAX_DAMAGE_ROWS];

        private static BuilderCodec<PageEventData> buildCodec() {
            BuilderCodec.Builder<PageEventData> builder =
                    BuilderCodec.builder(PageEventData.class, PageEventData::new);
            builder.append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add();
            builder.append(new KeyedCodec<>("@allowShieldCraft", Codec.BOOLEAN),
                    (d, v) -> d.allowShieldCraft = v, d -> d.allowShieldCraft).add();
            builder.append(new KeyedCodec<>("@shieldKeepsSignatureEnergyWhenSwapped", Codec.BOOLEAN),
                    (d, v) -> d.shieldKeepsSignatureEnergyWhenSwapped = v,
                    d -> d.shieldKeepsSignatureEnergyWhenSwapped).add();
            builder.append(new KeyedCodec<>("@visualShieldInPlayersBack", Codec.BOOLEAN),
                    (d, v) -> d.visualShieldInPlayersBack = v, d -> d.visualShieldInPlayersBack).add();
            builder.append(new KeyedCodec<>("@fallResistanceWhenBlocking", Codec.BOOLEAN),
                    (d, v) -> d.fallResistanceWhenBlocking = v, d -> d.fallResistanceWhenBlocking).add();
            builder.append(new KeyedCodec<>("@damageFromTheBackResistance", Codec.BOOLEAN),
                    (d, v) -> d.damageFromTheBackResistance = v, d -> d.damageFromTheBackResistance).add();
            builder.append(new KeyedCodec<>("@doubleJump", Codec.BOOLEAN),
                    (d, v) -> d.doubleJump = v, d -> d.doubleJump).add();
            builder.append(new KeyedCodec<>("@fallStarAttack", Codec.BOOLEAN),
                    (d, v) -> d.fallStarAttack = v, d -> d.fallStarAttack).add();
            builder.append(new KeyedCodec<>("@guardBash", Codec.BOOLEAN),
                    (d, v) -> d.guardBash = v, d -> d.guardBash).add();
            builder.append(new KeyedCodec<>("@guardBashPushLaunch", Codec.BOOLEAN),
                    (d, v) -> d.guardBashPushLaunch = v, d -> d.guardBashPushLaunch).add();
            builder.append(new KeyedCodec<>("@sprintAttack", Codec.BOOLEAN),
                    (d, v) -> d.sprintAttack = v, d -> d.sprintAttack).add();
            builder.append(new KeyedCodec<>("@kickPushAttack", Codec.BOOLEAN),
                    (d, v) -> d.kickPushAttack = v, d -> d.kickPushAttack).add();
            builder.append(new KeyedCodec<>("@throwAttack", Codec.BOOLEAN),
                    (d, v) -> d.throwAttack = v, d -> d.throwAttack).add();
            builder.append(new KeyedCodec<>("@throwLeftHand", Codec.BOOLEAN),
                    (d, v) -> d.throwLeftHand = v, d -> d.throwLeftHand).add();
            builder.append(new KeyedCodec<>("@furiousOnslaught", Codec.BOOLEAN),
                    (d, v) -> d.furiousOnslaught = v, d -> d.furiousOnslaught).add();
            builder.append(new KeyedCodec<>("@guardBashShockwaveWithMjolnir", Codec.BOOLEAN),
                    (d, v) -> d.guardBashShockwaveWithMjolnir = v,
                    d -> d.guardBashShockwaveWithMjolnir).add();

            builder.append(new KeyedCodec<>("@weaponMaxDurability", Codec.DOUBLE),
                    (d, v) -> d.weaponMaxDurability = v, d -> d.weaponMaxDurability).add();
            builder.append(new KeyedCodec<>("@craftModCompatibility", Codec.BOOLEAN),
                    (d, v) -> d.craftModCompatibility = v, d -> d.craftModCompatibility).add();
            builder.append(new KeyedCodec<>("@craftTimeSeconds", Codec.DOUBLE),
                    (d, v) -> d.craftTimeSeconds = v, d -> d.craftTimeSeconds).add();
            builder.append(new KeyedCodec<>("@craftBenchTier", Codec.DOUBLE),
                    (d, v) -> d.craftBenchTier = v, d -> d.craftBenchTier).add();
            builder.append(new KeyedCodec<>("@craftEndgameBenchTier", Codec.DOUBLE),
                    (d, v) -> d.craftEndgameBenchTier = v, d -> d.craftEndgameBenchTier).add();
            builder.append(new KeyedCodec<>("@craftRequiredMemoriesLevel", Codec.DOUBLE),
                    (d, v) -> d.craftRequiredMemoriesLevel = v, d -> d.craftRequiredMemoriesLevel).add();
            builder.append(new KeyedCodec<>("@damageModCompatibility", Codec.BOOLEAN),
                    (d, v) -> d.damageModCompatibility = v, d -> d.damageModCompatibility).add();
            for (int i = 0; i < MAX_INGREDIENT_ROWS; i++) {
                int index = i;
                builder.append(new KeyedCodec<>("@ingredientId" + (i + 1), Codec.STRING),
                        (d, v) -> d.ingredientIds[index] = v,
                        d -> d.ingredientIds[index]).add();
                builder.append(new KeyedCodec<>("@ingredientQty" + (i + 1), Codec.DOUBLE),
                        (d, v) -> d.ingredientQuantities[index] = v,
                        d -> d.ingredientQuantities[index]).add();
            }
            for (int i = 0; i < MAX_DAMAGE_ROWS; i++) {
                int index = i;
                builder.append(new KeyedCodec<>("@damageValue" + (i + 1), Codec.DOUBLE),
                        (d, v) -> d.damageValues[index] = v,
                        d -> d.damageValues[index]).add();
            }
            return builder.build();
        }
    }
}


