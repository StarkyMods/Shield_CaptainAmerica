package co.carrd.starkymods.config;

import co.carrd.starkymods.recipe.ShieldCapRecipeOverrideManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShieldCapCraftHotReloadService {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean pendingReload = new AtomicBoolean(false);
    private final AtomicBoolean startMessageSent = new AtomicBoolean(false);
    private final AtomicBoolean suppressMessagesForPendingReload = new AtomicBoolean(false);
    private final Map<UUID, PlayerRef> opPlayers = new ConcurrentHashMap<>();
    private final Object reloadLock = new Object();
    private Thread watcherThread;
    private volatile long lastFileChangeAtMillis = 0L;
    private volatile long nextRetryAtMillis = 0L;
    private volatile long lastInternalWriteAtMillis = 0L;

    public void markInternalCraftConfigWrite() {
        lastInternalWriteAtMillis = System.currentTimeMillis();
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }

        watcherThread = new Thread(this::watchLoop, "ShieldCapCraftHotReload");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void stop() {
        running.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    private void watchLoop() {
        File craftFile = ShieldCapCraftConfigManager.getCraftConfigFile();
        Path filePath = craftFile.toPath().toAbsolutePath().normalize();
        Path folderPath = filePath.getParent();
        String targetFileName = filePath.getFileName().toString();

        if (folderPath == null) {
            System.out.println("[ShieldCap] Craft hot reload disabled: craft config folder not found.");
            return;
        }

        try {
            Files.createDirectories(folderPath);

            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                folderPath.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                );

                System.out.println("[ShieldCap] Watching " + filePath + " for live craft reload.");

                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    boolean triggerReload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        Object context = event.context();
                        if (!(context instanceof Path changedPath)) {
                            continue;
                        }

                        if (targetFileName.equalsIgnoreCase(changedPath.getFileName().toString())) {
                            triggerReload = true;
                        }
                    }

                    key.reset();

                    if (triggerReload) {
                        long now = System.currentTimeMillis();
                        lastFileChangeAtMillis = now;
                        boolean isInternalWrite = (now - lastInternalWriteAtMillis) <= 1500L;
                        suppressMessagesForPendingReload.set(isInternalWrite);
                        pendingReload.set(true);
                        startMessageSent.set(false);
                        System.out.println("[ShieldCap] Detected change in shieldcapcraft.json, scheduling live craft reload.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ShieldCap] Craft hot reload watcher crashed:");
            e.printStackTrace();
        }
    }

    public void pollAndApplyIfPending(PlayerRef player, boolean isOp) {
        trackOpPlayer(player, isOp);

        if (!pendingReload.get()) {
            return;
        }

        if (!suppressMessagesForPendingReload.get() && startMessageSent.compareAndSet(false, true)) {
            broadcastToOpPlayers("Applying updated shieldcapcraft.json...");
        }

        long now = System.currentTimeMillis();
        if (now < nextRetryAtMillis || now - lastFileChangeAtMillis < 300L) {
            return;
        }

        synchronized (reloadLock) {
            if (!pendingReload.get()) {
                return;
            }

            boolean loaded = ShieldCapCraftConfigManager.reloadCraftIfValid();
            if (!loaded) {
                nextRetryAtMillis = System.currentTimeMillis() + 1000L;
                System.out.println("[ShieldCap] Live craft reload waiting: shieldcapcraft.json is invalid, keeping current recipe.");
                return;
            }

            boolean recipeApplied = ShieldCapRecipeOverrideManager.applyConfiguredRecipe();
            boolean durabilityAssetsApplied = ShieldCapDurabilityAssetOverrideManager.applyConfiguredDurabilityAssets();
            if (!recipeApplied || !durabilityAssetsApplied) {
                nextRetryAtMillis = System.currentTimeMillis() + 1000L;
                System.out.println("[ShieldCap] Live craft reload failed to apply assets, retry scheduled.");
                return;
            }

            Integer configuredMaxDurability =
                    ShieldCapCraftConfigManager.getConfig() != null ? ShieldCapCraftConfigManager.getConfig().weaponMaxDurability : null;
            int updatedExistingStacks = ShieldCapDurabilityLiveUpdater.applyEverywhereLoaded(configuredMaxDurability);
            if (updatedExistingStacks > 0) {
                System.out.println("[ShieldCap] Updated durability for existing ShieldCap stacks: " + updatedExistingStacks);
            }

            pendingReload.set(false);
            nextRetryAtMillis = 0L;
            System.out.println("[ShieldCap] Live craft reload applied from shieldcapcraft.json.");
            if (!suppressMessagesForPendingReload.get()) {
                broadcastToOpPlayers("Updated shieldcapcraft.json loaded.");
            }
            suppressMessagesForPendingReload.set(false);
        }
    }

    private void trackOpPlayer(PlayerRef player, boolean isOp) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUuid();
        if (uuid == null || !player.isValid()) {
            if (uuid != null) {
                opPlayers.remove(uuid);
            }
            return;
        }

        if (isOp) {
            opPlayers.put(uuid, player);
        } else {
            opPlayers.remove(uuid);
        }
    }

    private void broadcastToOpPlayers(String text) {
        Message message = Message.raw(text);
        Iterator<Map.Entry<UUID, PlayerRef>> iterator = opPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerRef> entry = iterator.next();
            PlayerRef ref = entry.getValue();
            if (ref == null || !ref.isValid()) {
                iterator.remove();
                continue;
            }
            ref.sendMessage(message);
        }
    }
}
