package co.carrd.starkymods;

import co.carrd.starkymods.interactions.ShieldCapRefreshVisualsInteraction;
import co.carrd.starkymods.interactions.ShieldCapPrimarySelector;
import co.carrd.starkymods.interactions.ShieldCapAirborneCondition;
import co.carrd.starkymods.interactions.ShieldCapPrimaryJumpHitCooldown;
import co.carrd.starkymods.interactions.ShieldCapPrimaryJumpStateTickSystem;
import co.carrd.starkymods.interactions.ShieldCapPrimaryCrouchChainCooldown;
import co.carrd.starkymods.interactions.ShieldCapPrimarySprintHitCooldown;
import co.carrd.starkymods.interactions.ShieldCapSprintCondition;
import co.carrd.starkymods.interactions.ShieldCapGuardInactiveCondition;
import co.carrd.starkymods.interactions.ShieldCapSecondaryGuardBashCooldown;
import co.carrd.starkymods.interactions.ShieldCapCatch;
import co.carrd.starkymods.interactions.ShieldCapSafeRemoveProjectile;
import co.carrd.starkymods.interactions.ShieldCapThrow;
import co.carrd.starkymods.interactions.ShieldCapForceReturn;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingEnable;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingBounce;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingImpact;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingLaunchConfigProjectile;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingLaunchProjectile;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingMarkProjectile;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingReturnOnMiss;
import co.carrd.starkymods.interactions.ShieldCapThrowKickBlockImpact;
import co.carrd.starkymods.interactions.ShieldCapThrowKickImpact;
import co.carrd.starkymods.interactions.ShieldCapThrowKickRecentMarker;
import co.carrd.starkymods.interactions.ShieldCapThrowKickWindowCondition;
import co.carrd.starkymods.interactions.ShieldCapThrowHomingTickSystem;
import co.carrd.starkymods.interactions.StarkyPluginPresentCondition;
import co.carrd.starkymods.visuals.ShieldCapBackModelSystems;
import co.carrd.starkymods.visuals.ShieldCapBackStateComponent;
import co.carrd.starkymods.visuals.ShieldCapReturnReticleInjector;
import co.carrd.starkymods.visuals.ShieldCapVisualSyncService;
import co.carrd.starkymods.interactions.ShieldCapReturnKickInputService;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class StarkyShieldCaptainAmerica extends JavaPlugin {
    private static StarkyShieldCaptainAmerica instance;

    private final ShieldCapVisualSyncService visualSyncService = new ShieldCapVisualSyncService();
    private final ShieldCapReturnKickInputService returnKickInputService = new ShieldCapReturnKickInputService();
    private final ShieldCapReturnReticleInjector returnReticleInjector = new ShieldCapReturnReticleInjector();
    private ComponentType<EntityStore, ShieldCapBackStateComponent> shieldCapBackStateComponentType;

    public static StarkyShieldCaptainAmerica getInstance() {
        return instance;
    }

    public ShieldCapVisualSyncService getVisualSyncService() {
        return visualSyncService;
    }

    public ComponentType<EntityStore, ShieldCapBackStateComponent> getShieldCapBackStateComponentType() {
        return shieldCapBackStateComponentType;
    }

    public StarkyShieldCaptainAmerica(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        super.setup();
        shieldCapBackStateComponentType =
                getEntityStoreRegistry().registerComponent(
                        ShieldCapBackStateComponent.class,
                        "ShieldCapBackState",
                        ShieldCapBackStateComponent.CODEC
                );
        ShieldCapBackModelSystems.registerSystems(this);
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Refresh_Java",
                ShieldCapRefreshVisualsInteraction.class,
                ShieldCapRefreshVisualsInteraction.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Primary_Selector_Java",
                ShieldCapPrimarySelector.class,
                ShieldCapPrimarySelector.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Airborne_Condition_Java",
                ShieldCapAirborneCondition.class,
                ShieldCapAirborneCondition.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Primary_Jump_Hit_Cooldown_Java",
                ShieldCapPrimaryJumpHitCooldown.class,
                ShieldCapPrimaryJumpHitCooldown.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Primary_Crouch_Chain_Cooldown_Java",
                ShieldCapPrimaryCrouchChainCooldown.class,
                ShieldCapPrimaryCrouchChainCooldown.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Primary_Sprint_Hit_Cooldown_Java",
                ShieldCapPrimarySprintHitCooldown.class,
                ShieldCapPrimarySprintHitCooldown.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Sprint_Condition_Java",
                ShieldCapSprintCondition.class,
                ShieldCapSprintCondition.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Guard_Inactive_Condition_Java",
                ShieldCapGuardInactiveCondition.class,
                ShieldCapGuardInactiveCondition.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Secondary_Guard_Bash_Cooldown_Java",
                ShieldCapSecondaryGuardBashCooldown.class,
                ShieldCapSecondaryGuardBashCooldown.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Catch",
                ShieldCapCatch.class,
                ShieldCapCatch.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Remove_Projectile_Safe",
                ShieldCapSafeRemoveProjectile.class,
                ShieldCapSafeRemoveProjectile.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw",
                ShieldCapThrow.class,
                ShieldCapThrow.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Force_Return_Java",
                ShieldCapForceReturn.class,
                ShieldCapForceReturn.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Enable",
                ShieldCapThrowHomingEnable.class,
                ShieldCapThrowHomingEnable.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Bounce",
                ShieldCapThrowHomingBounce.class,
                ShieldCapThrowHomingBounce.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Impact",
                ShieldCapThrowHomingImpact.class,
                ShieldCapThrowHomingImpact.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Launch_Config_Projectile",
                ShieldCapThrowHomingLaunchConfigProjectile.class,
                ShieldCapThrowHomingLaunchConfigProjectile.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Launch_Projectile",
                ShieldCapThrowHomingLaunchProjectile.class,
                ShieldCapThrowHomingLaunchProjectile.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Mark_Projectile",
                ShieldCapThrowHomingMarkProjectile.class,
                ShieldCapThrowHomingMarkProjectile.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Homing_Return_On_Miss",
                ShieldCapThrowHomingReturnOnMiss.class,
                ShieldCapThrowHomingReturnOnMiss.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Kick_Impact",
                ShieldCapThrowKickImpact.class,
                ShieldCapThrowKickImpact.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Kick_Block_Impact",
                ShieldCapThrowKickBlockImpact.class,
                ShieldCapThrowKickBlockImpact.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Kick_Recent_Java",
                ShieldCapThrowKickRecentMarker.class,
                ShieldCapThrowKickRecentMarker.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "ShieldCap_Throw_Kick_Window_Java",
                ShieldCapThrowKickWindowCondition.class,
                ShieldCapThrowKickWindowCondition.CODEC
        );
        getCodecRegistry(Interaction.CODEC).register(
                "StarkyPluginPresentCondition",
                StarkyPluginPresentCondition.class,
                StarkyPluginPresentCondition.CODEC
        );
        getEntityStoreRegistry().registerSystem(new ShieldCapThrowHomingTickSystem());
        getEntityStoreRegistry().registerSystem(new ShieldCapPrimaryJumpStateTickSystem());
        visualSyncService.register(this);
        returnKickInputService.register(this);
        returnReticleInjector.register(this);
        new HStats("3b08e6b8-d2cc-4c30-8b35-1dc475393fad", "1.0.0");
    }

    @Override
    protected void shutdown() {
        returnReticleInjector.shutdown();
        returnKickInputService.shutdown();
        visualSyncService.shutdown();
        if (instance == this) {
            instance = null;
        }
        super.shutdown();
    }
}
