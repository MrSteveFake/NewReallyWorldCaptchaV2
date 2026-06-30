package com.limbocaptcha.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.limbocaptcha.captcha.CaptchaManager;
import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.database.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ApiServer {

    private final ConfigManager configManager;
    private final CaptchaManager captchaManager;
    private final DatabaseManager databaseManager;
    private final Gson gson;
    private HttpServer server;

    public ApiServer(ConfigManager configManager, CaptchaManager captchaManager, DatabaseManager databaseManager) {
        this.configManager = configManager;
        this.captchaManager = captchaManager;
        this.databaseManager = databaseManager;
        this.gson = new Gson();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(configManager.getApiPort()), 0);
            server.createContext("/api/verify", new VerifyHandler());
            server.createContext("/api/check", new CheckHandler());
            server.createContext("/api/status", new StatusHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("[LimboCaptcha] API server started on port " + configManager.getApiPort());
        } catch (IOException e) {
            System.err.println("[LimboCaptcha] API server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            // CORS preflight
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                sendCors(ex);
                return;
            }

            System.out.println("[LimboCaptcha API] Received " + ex.getRequestMethod() + " request from " + ex.getRemoteAddress());

            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("[LimboCaptcha API] Request body: " + body);

            Map<String, String> params = parseFormData(body);

            String token = params.get("token");
            String recaptchaResponse = params.get("g-recaptcha-response");

            System.out.println("[LimboCaptcha API] Token: " + token);
            System.out.println("[LimboCaptcha API] reCAPTCHA response length: " + (recaptchaResponse != null ? recaptchaResponse.length() : 0));

            JsonObject response = new JsonObject();

            if (token == null || recaptchaResponse == null) {
                response.addProperty("success", false);
                response.addProperty("error", "Missing token or captcha response");
                System.out.println("[LimboCaptcha API] Missing parameters");
                sendJson(ex, 400, gson.toJson(response));
                return;
            }

            boolean valid = captchaManager.verifyCaptcha(token, recaptchaResponse);
            response.addProperty("success", valid);
            response.addProperty("token", token);

            if (valid) {
                captchaManager.markSuccess(token);
                response.addProperty("message", "Verification passed");
                response.addProperty("player", captchaManager.getPlayerNameByToken(token));
                System.out.println("[LimboCaptcha API] Verification PASSED for player: " + captchaManager.getPlayerNameByToken(token));
            } else {
                captchaManager.markFailed(token);
                response.addProperty("message", "Verification failed");
                System.out.println("[LimboCaptcha API] Verification FAILED");
            }

            String jsonResponse = gson.toJson(response);
            System.out.println("[LimboCaptcha API] Response: " + jsonResponse);
            sendJson(ex, 200, jsonResponse);
        }
    }

    class CheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                sendCors(ex);
                return;
            }

            String query = ex.getRequestURI().getQuery();
            String token = getQueryParam(query, "token");
            JsonObject response = new JsonObject();

            System.out.println("[LimboCaptcha API] Check token: " + token);

            if (token == null) {
                response.addProperty("valid", false);
                response.addProperty("error", "No token provided");
            } else {
                boolean valid = databaseManager.isValidToken(token);
                response.addProperty("valid", valid);
                response.addProperty("token", token);
                if (valid) {
                    String playerName = databaseManager.getPlayerNameByToken(token);
                    response.addProperty("player", playerName);
                    System.out.println("[LimboCaptcha API] Token valid for player: " + playerName);
                } else {
                    System.out.println("[LimboCaptcha API] Token NOT valid");
                }
            }

            sendJson(ex, 200, gson.toJson(response));
        }
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                sendCors(ex);
                return;
            }

            String query = ex.getRequestURI().getQuery();
            String token = getQueryParam(query, "token");
            JsonObject response = new JsonObject();

            if (token == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "No token provided");
            } else {
                String status = databaseManager.getTokenStatus(token);
                response.addProperty("token", token);
                response.addProperty("status", status != null ? status : "not_found");
                response.addProperty("player", databaseManager.getPlayerNameByToken(token));
            }

            sendJson(ex, 200, gson.toJson(response));
        }
    }

    private String getQueryParam(String query, String name) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        for (String p : body.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), 
                          URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private void sendCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.sendResponseHeaders(204, -1);
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        byte[] resp = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, resp.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(resp);
        }
    }
}
