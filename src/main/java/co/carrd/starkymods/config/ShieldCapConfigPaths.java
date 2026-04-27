package co.carrd.starkymods.config;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ShieldCapConfigPaths {
    private static final File LEGACY_FOLDER = new File("ShieldCap_Starky");
    private static final File FOLDER = new File(new File("mods"), "ShieldCap_Starky");

    private static boolean migrationAttempted = false;

    private ShieldCapConfigPaths() {
    }

    public static File getFolder() {
        migrateLegacyFolderIfNeeded();
        return FOLDER;
    }

    public static File getGeneratedFolder() {
        return new File(getFolder(), "generated");
    }

    public static synchronized void migrateLegacyFolderIfNeeded() {
        if (migrationAttempted) {
            return;
        }
        migrationAttempted = true;

        Path legacyPath = LEGACY_FOLDER.toPath().toAbsolutePath().normalize();
        Path targetPath = FOLDER.toPath().toAbsolutePath().normalize();

        if (legacyPath.equals(targetPath) || !Files.exists(legacyPath)) {
            return;
        }

        try {
            copyFolderContents(legacyPath, targetPath);
            deleteFolder(legacyPath);
            System.out.println("[ShieldCap] Migrated ShieldCap_Starky config folder to " + targetPath);
        } catch (Exception e) {
            System.out.println("[ShieldCap] Failed to migrate ShieldCap_Starky config folder from "
                    + legacyPath + " to " + targetPath);
            e.printStackTrace();
        }
    }

    private static void copyFolderContents(Path sourceRoot, Path targetRoot) throws IOException {
        Files.createDirectories(targetRoot);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.forEach(source -> {
                try {
                    Path relative = sourceRoot.relativize(source);
                    Path target = targetRoot.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void deleteFolder(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
