package com.limbocaptcha.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.limbocaptcha.captcha.CaptchaManager;
import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.database.DatabaseManager;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiServer {
    private final ConfigManager cm;
    private final CaptchaManager cap;
    private final DatabaseManager dm;
    private final Gson g = new Gson();
    private HttpServer s;

    public ApiServer(ConfigManager cm, CaptchaManager cap, DatabaseManager dm) {
        this.cm = cm; this.cap = cap; this.dm = dm;
    }

    public void start() {
        try {
            s = HttpServer.create(new InetSocketAddress(cm.getApiPort()), 0);
            s.createContext("/api/verify", new V());
            s.createContext("/api/check", new C());
            s.setExecutor(null);
            s.start();
            System.out.println("[LimboCaptcha] API server started on port " + cm.getApiPort());
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void stop() { if (s != null) s.stop(0); }

    class V implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { c(ex); return; }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("[API] Received: " + body);

            Map<String, String> m = new HashMap<>();
            for (String p : body.split("&")) {
                String[] kv = p.split("=", 2);
                if (kv.length == 2) m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }

            String token = m.get("token");
            String recaptcha = m.get("g-recaptcha-response");

            System.out.println("[API] Token: " + token);
            System.out.println("[API] reCAPTCHA length: " + (recaptcha != null ? recaptcha.length() : 0));

            JsonObject j = new JsonObject();

            if (token == null || recaptcha == null) {
                j.addProperty("success", false);
                j.addProperty("error", "Missing parameters");
                j(ex, 400, g.toJson(j));
                return;
            }

            boolean ok = cap.verifyCaptcha(token, recaptcha);
            j.addProperty("success", ok);

            if (ok) {
                cap.markSuccess(token);
                j.addProperty("player", cap.getPlayerNameByToken(token));
                System.out.println("[API] VERIFICATION SUCCESS for token: " + token);
            } else {
                cap.markFailed(token);
                System.out.println("[API] VERIFICATION FAILED for token: " + token);
            }

            String response = g.toJson(j);
            System.out.println("[API] Response: " + response);
            j(ex, 200, response);
        }
    }

    class C implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { c(ex); return; }
            String t = qp(ex.getRequestURI().getQuery(), "token");
            JsonObject j = new JsonObject();
            if (t == null) j.addProperty("valid", false);
            else { boolean v = dm.isValidToken(t); j.addProperty("valid", v); if (v) j.addProperty("player", dm.getPlayerNameByToken(t)); }
            j(ex, 200, g.toJson(j));
        }
    }

    private String qp(String q, String n) {
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(n)) return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private void c(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.sendResponseHeaders(204, -1);
    }

    private void j(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] r = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, r.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(r); }
    }
}
