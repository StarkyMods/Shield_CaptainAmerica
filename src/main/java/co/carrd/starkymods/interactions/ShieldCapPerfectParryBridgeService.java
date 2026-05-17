package co.carrd.starkymods.interactions;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ShieldCapPerfectParryBridgeService {
    private static final boolean DEBUG = false;
    private static final String LOG_PREFIX = "[ShieldCapPerfectParryDebug] ";
    private static final String PERFECT_PARRY_EVENT_CLASS = "org.narwhals.plugin.api.events.PerfectParryEvent";
    private static final String MAIN_SHIELD_ID = "Weapon_Shield_CaptainAmerica_Starky";
    private static final String LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainAmerica_Starky";
    private static final String VIBRANIUM_MAIN_SHIELD_ID = "Weapon_Shield_Vibranium_Starky";
    private static final String VIBRANIUM_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Vibranium_Starky";
    private static final String CARTER_MAIN_SHIELD_ID = "Weapon_Shield_CaptainCarter_Starky";
    private static final String CARTER_LEFT_SHIELD_ID = "Weapon_ShieldLeft_CaptainCarter_Starky";
    private static final String GEORGIO_MAIN_SHIELD_ID = "Weapon_Shield_Georgio_Starky";
    private static final String GEORGIO_LEFT_SHIELD_ID = "Weapon_ShieldLeft_Georgio_Starky";
    private static final String PERFECT_PARRY_ROOT_ID = "Root_ShieldCap_Perfect_Parry_Shockwave";
    private static final long PERFECT_PARRY_IMPACT_SUPPRESSION_WINDOW_MS = 1500L;
    private static final InteractionType[] FORCED_LANES = {
            InteractionType.Ability2,
            InteractionType.Secondary,
            InteractionType.Primary
    };
    private static final InteractionType[] CANDIDATE_LANES = {
            InteractionType.Primary,
            InteractionType.Secondary,
            InteractionType.Ability1,
            InteractionType.Ability2,
            InteractionType.Ability3
    };
    private static final Map<UUID, Long> PERFECT_PARRY_ACTIVE_UNTIL_MS = new ConcurrentHashMap<>();
    private static volatile boolean PERFECT_PARRY_SUPPORT_ACTIVE = false;

    private EventRegistration<?, ?> perfectParryRegistration;

    public void register(JavaPlugin plugin) {
        Class<?> eventClass = resolvePerfectParryEventClass();
        if (eventClass == null) {
            PERFECT_PARRY_SUPPORT_ACTIVE = false;
            log("register skipped | reason=event class not found");
            return;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        EventRegistration<?, ?> registration =
                plugin.getEventRegistry().registerGlobal((Class) eventClass, (Consumer) this::handlePerfectParryEvent);
        perfectParryRegistration = registration;
        PERFECT_PARRY_SUPPORT_ACTIVE = true;
        log("registered | eventClass=" + eventClass.getName());
    }

    public void shutdown() {
        if (perfectParryRegistration != null) {
            perfectParryRegistration.unregister();
            perfectParryRegistration = null;
        }
        PERFECT_PARRY_SUPPORT_ACTIVE = false;
        PERFECT_PARRY_ACTIVE_UNTIL_MS.clear();
    }

    public static boolean isPerfectParrySupportActive() {
        return PERFECT_PARRY_SUPPORT_ACTIVE;
    }

    private void handlePerfectParryEvent(Object event) {
        log("event received | class=" + (event == null ? "null" : event.getClass().getName()));
        Ref<EntityStore> defenderRef = invokeEntityRefGetter(event, "getDefender");
        if (defenderRef == null) {
            log("event ignored | reason=no defender ref");
            return;
        }
        if (!defenderRef.isValid()) {
            log("event ignored | reason=invalid defender ref");
            return;
        }
        if (!hasShieldEquipped(defenderRef)) {
            log("event ignored | reason=shield not equipped");
            return;
        }

        markPerfectParryWindow(defenderRef);
        log("event accepted");
        triggerPerfectParryShockwave(defenderRef);
    }

    public static boolean isPerfectParryWindowActive(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long untilMs = PERFECT_PARRY_ACTIVE_UNTIL_MS.get(playerUuid);
        if (untilMs == null) {
            return false;
        }
        if (untilMs < now) {
            PERFECT_PARRY_ACTIVE_UNTIL_MS.remove(playerUuid, untilMs);
            return false;
        }
        return true;
    }

    private void triggerPerfectParryShockwave(Ref<EntityStore> defenderRef) {
        RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(PERFECT_PARRY_ROOT_ID);
        if (rootInteraction == null || defenderRef.getStore() == null) {
            log("shockwave skipped | reason=" + (rootInteraction == null ? "missing root" : "missing store"));
            return;
        }

        defenderRef.getStore().forEachChunk((chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
                if (playerRef == null || !playerRef.isValid() || !playerRef.equals(defenderRef)) {
                    continue;
                }

                InteractionManager interactionManager =
                        commandBuffer.getComponent(playerRef, InteractionModule.get().getInteractionManagerComponent());
                if (interactionManager == null) {
                    log("shockwave skipped | reason=no interaction manager");
                    return;
                }

                for (InteractionType lane : CANDIDATE_LANES) {
                    try {
                        InteractionContext context =
                                InteractionContext.forInteraction(interactionManager, playerRef, lane, commandBuffer);
                        boolean started = interactionManager.tryStartChain(playerRef, commandBuffer, lane, context, rootInteraction);
                        log("tryStartChain | lane=" + lane + " | started=" + started);
                        if (started) {
                            return;
                        }
                    } catch (Throwable t) {
                        log("tryStartChain error | lane=" + lane + " | error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                }

                for (InteractionType lane : FORCED_LANES) {
                    boolean canRun = false;
                    try {
                        canRun = interactionManager.canRun(lane, rootInteraction);
                    } catch (Throwable ignored) {
                    }

                    try {
                        InteractionContext context =
                                InteractionContext.forInteraction(interactionManager, playerRef, lane, commandBuffer);
                        interactionManager.startChain(playerRef, commandBuffer, lane, context, rootInteraction);
                        log("startChain | lane=" + lane + " | started=true | canRun=" + canRun);
                        return;
                    } catch (Throwable t) {
                        log("startChain error | lane=" + lane + " | canRun=" + canRun
                                + " | error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                }

                log("shockwave failed | reason=no lane accepted");
                return;
            }
        });
    }

    private void markPerfectParryWindow(Ref<EntityStore> defenderRef) {
        if (defenderRef == null || !defenderRef.isValid() || defenderRef.getStore() == null) {
            return;
        }

        Player player = defenderRef.getStore().getComponent(defenderRef, Player.getComponentType());
        UUID playerUuid = player != null && player.getPlayerRef() != null ? player.getPlayerRef().getUuid() : null;
        if (playerUuid != null) {
            PERFECT_PARRY_ACTIVE_UNTIL_MS.put(
                    playerUuid,
                    System.currentTimeMillis() + PERFECT_PARRY_IMPACT_SUPPRESSION_WINDOW_MS
            );
        }
    }

    private boolean hasShieldEquipped(Ref<EntityStore> defenderRef) {
        if (defenderRef.getStore() == null) {
            return false;
        }

        Player player = defenderRef.getStore().getComponent(defenderRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return false;
        }

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        byte activeHotbarSlot = inventory.getActiveHotbarSlot();
        if (isValidSlot(hotbar, activeHotbarSlot)
                && (matchesId(hotbar.getItemStack(activeHotbarSlot), MAIN_SHIELD_ID)
                || matchesId(hotbar.getItemStack(activeHotbarSlot), VIBRANIUM_MAIN_SHIELD_ID)
                || matchesId(hotbar.getItemStack(activeHotbarSlot), CARTER_MAIN_SHIELD_ID)
                || matchesId(hotbar.getItemStack(activeHotbarSlot), GEORGIO_MAIN_SHIELD_ID))) {
            log("shield equipped | hand=main | slot=" + activeHotbarSlot);
            return true;
        }

        ItemContainer utility = inventory.getUtility();
        byte activeUtilitySlot = inventory.getActiveUtilitySlot();
        boolean leftEquipped = isValidSlot(utility, activeUtilitySlot)
                && (matchesId(utility.getItemStack(activeUtilitySlot), LEFT_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), MAIN_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), VIBRANIUM_LEFT_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), VIBRANIUM_MAIN_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), CARTER_LEFT_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), CARTER_MAIN_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), GEORGIO_LEFT_SHIELD_ID)
                || matchesId(utility.getItemStack(activeUtilitySlot), GEORGIO_MAIN_SHIELD_ID));
        if (leftEquipped) {
            log("shield equipped | hand=left | slot=" + activeUtilitySlot);
        }
        return leftEquipped;
    }

    private boolean isValidSlot(@Nullable ItemContainer container, byte slot) {
        return container != null
                && slot != Inventory.INACTIVE_SLOT_INDEX
                && slot >= 0
                && slot < container.getCapacity();
    }

    private boolean matchesId(@Nullable ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }

        String stackItemId = stack.getItemId();
        if (stackItemId == null || stackItemId.isBlank()) {
            return false;
        }

        return stackItemId.equals(itemId)
                || stackItemId.endsWith("." + itemId)
                || stackItemId.contains(itemId);
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> invokeEntityRefGetter(Object event, String methodName) {
        if (event == null) {
            return null;
        }

        try {
            Method method = event.getClass().getMethod(methodName);
            Object result = method.invoke(event);
            if (result instanceof Ref<?>) {
                return (Ref<EntityStore>) result;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Class<?> resolvePerfectParryEventClass() {
        try {
            return Class.forName(PERFECT_PARRY_EVENT_CLASS);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void log(String message) {
        if (DEBUG) {
            System.out.println(LOG_PREFIX + message);
        }
    }
}
