package com.limbocaptcha.captcha;

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

public class WebServer {

    private final ConfigManager cm;
    private final DatabaseManager dm;
    private final CaptchaManager cap;
    private HttpServer server;

    public WebServer(ConfigManager cm, DatabaseManager dm, CaptchaManager cap) {
        this.cm = cm;
        this.dm = dm;
        this.cap = cap;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(cm.getWebPort()), 0);
            
            // Прием результата от сайта (САМЫЙ ВАЖНЫЙ)
            server.createContext("/submit", new SubmitHandler());
            
            // Проверка токена
            server.createContext("/check", new CheckHandler());
            
            server.setExecutor(null);
            server.start();
            System.out.println("[LimboCaptcha] WebServer started on port " + cm.getWebPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // Сайт отправляет сюда результат
    class SubmitHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                ex.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(ex.getRequestMethod())) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("[SUBMIT] Received: " + body);

                String token = null;
                String recaptcha = "";

                for (String p : body.split("&")) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                        String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        if (key.equals("token")) token = val;
                        if (key.equals("g-recaptcha-response")) recaptcha = val;
                    }
                }

                boolean ok = cap.verifyCaptcha(token, recaptcha);
                
                if (ok) {
                    cap.markSuccess(token);
                    System.out.println("[SUBMIT] SUCCESS for token: " + token);
                } else {
                    cap.markFailed(token);
                    System.out.println("[SUBMIT] FAILED for token: " + token);
                }

                String json = "{\"success\":" + ok + "}";
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                byte[] resp = json.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        }
    }

    // Проверка токена
    class CheckHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                ex.sendResponseHeaders(204, -1);
                return;
            }

            String query = ex.getRequestURI().getQuery();
            String token = null;
            if (query != null) {
                for (String p : query.split("&")) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2 && kv[0].equals("token")) {
                        token = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            }

            boolean valid = token != null && dm.isValidToken(token);
            String player = valid ? dm.getPlayerNameByToken(token) : "unknown";
            String json = "{\"valid\":" + valid + ",\"player\":\"" + player + "\"}";
            
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            byte[] resp = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        }
    }
}
