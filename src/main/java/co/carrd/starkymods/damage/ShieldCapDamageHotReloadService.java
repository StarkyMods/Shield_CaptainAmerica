package co.carrd.starkymods.damage;

import co.carrd.starkymods.config.ShieldCapDamageConfigManager;
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

public class ShieldCapDamageHotReloadService {
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

    public void markInternalDamageConfigWrite() {
        lastInternalWriteAtMillis = System.currentTimeMillis();
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }
        watcherThread = new Thread(this::watchLoop, "ShieldCapDamageHotReload");
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
        File damageFile = ShieldCapDamageConfigManager.getDamageConfigFile();
        Path filePath = damageFile.toPath().toAbsolutePath().normalize();
        Path folderPath = filePath.getParent();
        String targetFileName = filePath.getFileName().toString();

        if (folderPath == null) {
            System.out.println("[ShieldCap] Damage hot reload disabled: damage config folder not found.");
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
                System.out.println("[ShieldCap] Watching " + filePath + " for live damage reload.");
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
                        if (context instanceof Path changedPath
                                && targetFileName.equalsIgnoreCase(changedPath.getFileName().toString())) {
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
                        System.out.println("[ShieldCap] Detected change in shieldcapdamages.json, scheduling live damage reload.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ShieldCap] Damage hot reload watcher crashed:");
            e.printStackTrace();
        }
    }

    public void pollAndApplyIfPending(PlayerRef player, boolean isOp) {
        trackOpPlayer(player, isOp);
        if (!pendingReload.get()) {
            return;
        }
        if (!suppressMessagesForPendingReload.get() && startMessageSent.compareAndSet(false, true)) {
            broadcastToOpPlayers("Applying updated shieldcapdamages.json...");
        }
        long now = System.currentTimeMillis();
        if (now < nextRetryAtMillis || now - lastFileChangeAtMillis < 300L) {
            return;
        }
        synchronized (reloadLock) {
            if (!pendingReload.get()) {
                return;
            }
            var previous = ShieldCapDamageConfigManager.getConfigSnapshot();
            if (!ShieldCapDamageConfigManager.reloadDamagesIfValid()) {
                nextRetryAtMillis = System.currentTimeMillis() + 1000L;
                System.out.println("[ShieldCap] Live damage reload waiting: shieldcapdamages.json is invalid.");
                return;
            }
            markInternalDamageConfigWrite();
            boolean markerWritten = ShieldCapDamageConfigManager.disableDamageCompatibilityProfileIfValuesChangedAndSave(previous);
            if (!ShieldCapDamageAssetGenerator.generateAndReload()) {
                nextRetryAtMillis = System.currentTimeMillis() + 1000L;
                System.out.println("[ShieldCap] Live damage reload failed to apply assets, retry scheduled.");
                return;
            }
            pendingReload.set(false);
            nextRetryAtMillis = 0L;
            if (markerWritten) {
                System.out.println("[ShieldCap] Damage/force override detected, Mod Compatibility is now false in shieldcapdamages.json.");
            }
            System.out.println("[ShieldCap] Live damage reload applied from shieldcapdamages.json.");
            if (!suppressMessagesForPendingReload.get()) {
                broadcastToOpPlayers("Updated shieldcapdamages.json loaded.");
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
