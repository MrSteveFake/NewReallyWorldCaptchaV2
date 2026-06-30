package com.limbocaptcha.captcha;

import com.limbocaptcha.config.ConfigManager;
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
    private final WebServer webServer;
    private final Map<UUID, CompletableFuture<Boolean>> pending;

    public CaptchaManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.pending = new ConcurrentHashMap<>();
        this.webServer = new WebServer(configManager, this);
        webServer.start();
    }

    public CompletableFuture<Boolean> requestVerification(UUID uuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(uuid, future);
        future.orTimeout(configManager.getCaptchaTimeout(), TimeUnit.MINUTES)
            .exceptionally(t -> { pending.remove(uuid); return false; });
        return future;
    }

    public boolean verifyCaptcha(String response) {
        if (response == null || response.isEmpty()) return false;
        try {
            String params = "secret=" + URLEncoder.encode(configManager.getSecretKey(), StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(response, StandardCharsets.UTF_8);
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

    public void complete(UUID uuid, boolean success) {
        CompletableFuture<Boolean> f = pending.remove(uuid);
        if (f != null && !f.isDone()) f.complete(success);
    }

    public void shutdown() {
        pending.values().forEach(f -> { if (!f.isDone()) f.complete(false); });
        pending.clear();
        webServer.stop();
    }

    public ConfigManager getConfigManager() { return configManager; }
}
