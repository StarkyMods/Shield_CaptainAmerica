package co.carrd.starkymods.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShieldCapUpdateCheckService {
    private static final String MANIFEST_PATH = "manifest.json";
    private static final String UPDATE_MESSAGE = "There is a new update for Starky's Shield Captain America mod available now!";
    private static final String UPDATE_URL = "https://www.curseforge.com/hytale/mods/starky-shield";
    private static final URI REMOTE_VERSION_URI =
            URI.create("https://raw.githubusercontent.com/StarkyMods/StarkyModsUpdatesCheck/master/SHIELDCAP.json");
    private static final Pattern VERSION_TOKEN_PATTERN = Pattern.compile("[0-9]+|[A-Za-z]+");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final String LOCAL_VERSION = readLocalManifestVersion();

    private ShieldCapUpdateCheckService() {
    }

    public static void checkForPlayer(World world, UUID playerUuid) {
        if (world == null || playerUuid == null || !world.isAlive() || !isPrivilegedPlayer(playerUuid)) {
            return;
        }

        String localVersion = normalizeVersion(LOCAL_VERSION);
        if (localVersion == null) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(REMOTE_VERSION_URI)
                .GET()
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("User-Agent", "ShieldCap-Update-Check")
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(ShieldCapUpdateCheckService::extractRemoteVersion)
                .exceptionally(ignored -> null)
                .thenAccept(remoteVersion -> notifyPlayerIfUpdateExists(world, playerUuid, localVersion, remoteVersion));
    }

    private static void notifyPlayerIfUpdateExists(World world,
                                                   UUID playerUuid,
                                                   String localVersion,
                                                   String remoteVersion) {
        if (world == null || !world.isAlive() || remoteVersion == null || compareVersions(localVersion, remoteVersion) >= 0) {
            return;
        }

        world.execute(() -> {
            if (!world.isAlive() || !isPrivilegedPlayer(playerUuid)) {
                return;
            }

            PlayerRef playerRef = resolvePlayerRef(world, playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            playerRef.sendMessage(Message.raw(UPDATE_MESSAGE).link(UPDATE_URL));
        });
    }

    private static boolean isPrivilegedPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        PermissionsModule permissionsModule = PermissionsModule.get();
        if (permissionsModule == null) {
            return false;
        }

        return permissionsModule.hasPermission(playerUuid, "*")
                || permissionsModule.hasPermission(playerUuid, "owner")
                || permissionsModule.hasPermission(playerUuid, "Owner");
    }

    private static PlayerRef resolvePlayerRef(World world, UUID playerUuid) {
        if (world == null || playerUuid == null) {
            return null;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            if (playerUuid.equals(playerRef.getUuid())) {
                return playerRef;
            }
        }

        return null;
    }

    private static String extractRemoteVersion(HttpResponse<String> response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonPrimitive() && root.getAsJsonPrimitive().isString()) {
                return normalizeVersion(root.getAsString());
            }
            if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                return normalizeVersion(readObjectVersion(object, "Version", "version", "latestVersion", "LatestVersion"));
            }
        } catch (Exception ignored) {
            return normalizeVersion(body);
        }

        return null;
    }

    private static String readObjectVersion(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null || !object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }

            try {
                String value = object.get(key).getAsString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private static String readLocalManifestVersion() {
        String version = readVersionFromResource(MANIFEST_PATH);
        if (version != null) {
            return version;
        }

        version = readVersionFromResource("/" + MANIFEST_PATH);
        if (version != null) {
            return version;
        }

        version = readVersionFromFile(new File("src/main/resources/" + MANIFEST_PATH));
        if (version != null) {
            return version;
        }

        return readVersionFromFile(new File(MANIFEST_PATH));
    }

    private static String readVersionFromResource(String path) {
        String normalizedPath = path != null && path.startsWith("/") ? path : "/" + path;
        try (InputStream stream = ShieldCapUpdateCheckService.class.getResourceAsStream(normalizedPath)) {
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
        if (stream == null) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return normalizeVersion(readObjectVersion(root, "Version", "version"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeVersion(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized.isEmpty() ? null : normalized;
    }

    static int compareVersions(String leftVersion, String rightVersion) {
        String normalizedLeft = normalizeVersion(leftVersion);
        String normalizedRight = normalizeVersion(rightVersion);

        if (normalizedLeft == null && normalizedRight == null) {
            return 0;
        }
        if (normalizedLeft == null) {
            return -1;
        }
        if (normalizedRight == null) {
            return 1;
        }

        List<String> leftTokens = tokenizeVersion(normalizedLeft);
        List<String> rightTokens = tokenizeVersion(normalizedRight);
        int sharedLength = Math.min(leftTokens.size(), rightTokens.size());

        for (int index = 0; index < sharedLength; index++) {
            int tokenComparison = compareToken(leftTokens.get(index), rightTokens.get(index));
            if (tokenComparison != 0) {
                return tokenComparison;
            }
        }

        int leftTailSign = remainingTailSign(leftTokens, sharedLength);
        int rightTailSign = remainingTailSign(rightTokens, sharedLength);
        return Integer.compare(leftTailSign, rightTailSign);
    }

    private static List<String> tokenizeVersion(String version) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = VERSION_TOKEN_PATTERN.matcher(version);
        while (matcher.find()) {
            String token = matcher.group();
            if (token != null && !token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static int compareToken(String leftToken, String rightToken) {
        boolean leftNumeric = Character.isDigit(leftToken.charAt(0));
        boolean rightNumeric = Character.isDigit(rightToken.charAt(0));

        if (leftNumeric && rightNumeric) {
            return compareNumericToken(leftToken, rightToken);
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? 1 : -1;
        }

        return leftToken.compareToIgnoreCase(rightToken);
    }

    private static int compareNumericToken(String leftToken, String rightToken) {
        String normalizedLeft = stripLeadingZeros(leftToken);
        String normalizedRight = stripLeadingZeros(rightToken);

        if (normalizedLeft.length() != normalizedRight.length()) {
            return Integer.compare(normalizedLeft.length(), normalizedRight.length());
        }

        return normalizedLeft.compareTo(normalizedRight);
    }

    private static String stripLeadingZeros(String token) {
        int index = 0;
        while (index < token.length() - 1 && token.charAt(index) == '0') {
            index++;
        }
        return token.substring(index);
    }

    private static int remainingTailSign(List<String> tokens, int startIndex) {
        for (int index = startIndex; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token == null || token.isBlank()) {
                continue;
            }

            if (Character.isDigit(token.charAt(0))) {
                if (compareNumericToken(token, "0") > 0) {
                    return 1;
                }
                continue;
            }

            return -1;
        }

        return 0;
    }
}
