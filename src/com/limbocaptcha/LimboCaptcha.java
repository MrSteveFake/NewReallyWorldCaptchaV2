package com.limbocaptcha.captcha;

import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.database.DatabaseManager;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CaptchaManager {

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final WebServer webServer;
    private final Map<UUID, CompletableFuture<Boolean>> pending;
    private final Map<UUID, String> playerTokens;

    public CaptchaManager(ConfigManager configManager, DatabaseManager databaseManager) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.pending = new ConcurrentHashMap<>();
        this.playerTokens = new ConcurrentHashMap<>();
        this.webServer = new WebServer(configManager, databaseManager, this);
        webServer.start();
    }

    public String createToken(UUID uuid, String playerName, String ip) {
        // Удаляем старый токен если есть
        String oldToken = playerTokens.remove(uuid);
        if (oldToken != null) {
            CompletableFuture<Boolean> oldFuture = pending.remove(uuid);
            if (oldFuture != null && !oldFuture.isDone()) {
                oldFuture.complete(false);
            }
        }

        // Создаем новый токен
        String token = uuid.toString() + "_" + playerName;
        playerTokens.put(uuid, token);
        databaseManager.createVerification(uuid, playerName, token, ip);
        return token;
    }

    public CompletableFuture<Boolean> requestVerification(UUID uuid) {
        // Удаляем старый future если есть
        CompletableFuture<Boolean> oldFuture = pending.remove(uuid);
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.complete(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(uuid, future);
        future.orTimeout(configManager.getCaptchaTimeout(), TimeUnit.MINUTES)
            .exceptionally(t -> { 
                pending.remove(uuid); 
                playerTokens.remove(uuid); 
                return false; 
            });
        return future;
    }

    public boolean verifyCaptcha(String token, String recaptchaResponse) {
        if (token == null || recaptchaResponse == null || recaptchaResponse.isEmpty()) return false;
        if (!databaseManager.isValidToken(token)) return false;

        try {
            String params = "secret=" + URLEncoder.encode(configManager.getSecretKey(), StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(recaptchaResponse, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URI(
                "https://www.google.com/recaptcha/api/siteverify").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() == 200) {
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return body.contains("\"success\": true") || body.contains("\"success\":true");
            }
        } catch (Exception e) {
            System.err.println("Captcha error: " + e.getMessage());
        }
        return false;
    }

    public void markSuccess(String token) {
        databaseManager.markSuccess(token);
        String uuid = databaseManager.getPlayerUUIDByToken(token);
        if (uuid != null) {
            UUID playerUuid = UUID.fromString(uuid);
            CompletableFuture<Boolean> f = pending.get(playerUuid);
            if (f != null && !f.isDone()) f.complete(true);
        }
    }

    public void markFailed(String token) {
        databaseManager.markFailed(token);
        String uuid = databaseManager.getPlayerUUIDByToken(token);
        if (uuid != null) {
            UUID playerUuid = UUID.fromString(uuid);
            CompletableFuture<Boolean> f = pending.get(playerUuid);
            if (f != null && !f.isDone()) f.complete(false);
        }
    }

    public String getPlayerNameByToken(String token) {
        return databaseManager.getPlayerNameByToken(token);
    }

    public void shutdown() {
        pending.values().forEach(f -> { if (!f.isDone()) f.complete(false); });
        pending.clear();
        playerTokens.clear();
        webServer.stop();
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
}
