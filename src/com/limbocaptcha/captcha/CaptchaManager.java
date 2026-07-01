package com.limbocaptcha.captcha;

import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.database.DatabaseManager;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {

    private final ConfigManager cm;
    private final DatabaseManager dm;
    private final Map<UUID, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
    private static final SecureRandom R = new SecureRandom();
    private static final String CH = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public CaptchaManager(ConfigManager cm, DatabaseManager dm) {
        this.cm = cm;
        this.dm = dm;
    }

    private String rs(int l) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < l; i++) s.append(CH.charAt(R.nextInt(CH.length())));
        return s.toString();
    }

    public String createToken(UUID u, String name, String ip) {
        String old = tokens.remove(u);
        if (old != null) {
            CompletableFuture<Boolean> f = pending.remove(u);
            if (f != null && !f.isDone()) f.complete(false);
        }
        String token = rs(32) + "_" + name;
        tokens.put(u, token);
        dm.createVerification(u, name, token, ip);
        return token;
    }

    public CompletableFuture<Boolean> requestVerification(UUID u) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        pending.put(u, f);
        return f;
    }

    public void cancelVerification(UUID u) {
        CompletableFuture<Boolean> f = pending.remove(u);
        if (f != null && !f.isDone()) f.complete(false);
        tokens.remove(u);
    }

    public boolean verifyCaptcha(String token, String recaptcha) {
        if (token == null || recaptcha == null || recaptcha.isEmpty()) return false;
        if (!dm.isValidToken(token)) return false;

        try {
            String params = "secret=" + URLEncoder.encode(cm.getSecretKey(), StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(recaptcha, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URI(
                "https://www.google.com/recaptcha/api/siteverify").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() == 200) {
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return body.contains("\"success\": true") || body.contains("\"success\":true");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void markSuccess(String token) {
        dm.markSuccess(token);
        String uid = dm.getPlayerUUIDByToken(token);
        if (uid != null) {
            try {
                UUID u = UUID.fromString(uid);
                CompletableFuture<Boolean> f = pending.get(u);
                if (f != null && !f.isDone()) f.complete(true);
            } catch (Exception e) {}
        }
    }

    public void markFailed(String token) {
        dm.markFailed(token);
        String uid = dm.getPlayerUUIDByToken(token);
        if (uid != null) {
            try {
                UUID u = UUID.fromString(uid);
                CompletableFuture<Boolean> f = pending.get(u);
                if (f != null && !f.isDone()) f.complete(false);
            } catch (Exception e) {}
        }
    }

    public String getPlayerNameByToken(String t) { return dm.getPlayerNameByToken(t); }

    public void shutdown() {
        pending.values().forEach(f -> { if (!f.isDone()) f.complete(false); });
        pending.clear();
        tokens.clear();
    }

    public ConfigManager getConfigManager() { return cm; }
}
