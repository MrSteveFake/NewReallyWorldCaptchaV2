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

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final CaptchaManager captchaManager;
    private HttpServer server;

    public WebServer(ConfigManager configManager, DatabaseManager databaseManager, CaptchaManager captchaManager) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.captchaManager = captchaManager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(configManager.getWebPort()), 0);
            server.createContext("/captcha", new CaptchaPageHandler());
            server.createContext("/verify", new VerifyHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("[LimboCaptcha] Web server started on port " + configManager.getWebPort());
        } catch (IOException e) {
            System.err.println("[LimboCaptcha] WebServer error: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    class CaptchaPageHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
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

            if (token == null || !databaseManager.isValidToken(token)) {
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                    "<title>Invalid Token</title>" +
                    "<style>body{background:#f8d7da;display:flex;justify-content:center;" +
                    "align-items:center;height:100vh;font-family:sans-serif;margin:0;}" +
                    ".box{background:white;padding:40px;border-radius:20px;text-align:center;}" +
                    "h1{color:#721c24;}</style></head><body><div class='box'>" +
                    "<h1>Invalid or Expired Token</h1>" +
                    "<p>This verification link is not valid or has expired.</p>" +
                    "</div></body></html>";

                ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
                return;
            }

            String key = configManager.getSiteKey();
            String html = "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">" +
                "<title>Captcha Verification</title>" +
                "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>" +
                "<style>" +
                "body{display:flex;justify-content:center;align-items:center;height:100vh;" +
                "background:linear-gradient(135deg,#1e3c72,#2a5298);font-family:sans-serif;margin:0;}" +
                ".box{background:white;padding:40px;border-radius:20px;text-align:center;max-width:400px;" +
                "box-shadow:0 20px 60px rgba(0,0,0,0.3);}" +
                "h2{color:#333;margin-bottom:10px;}" +
                "p{color:#666;margin-bottom:25px;}" +
                "button{background:linear-gradient(135deg,#1e3c72,#2a5298);color:white;border:none;" +
                "padding:12px 30px;border-radius:25px;font-size:16px;cursor:pointer;margin-top:15px;}" +
                "button:hover{transform:scale(1.05);transition:0.2s;}" +
                "</style></head><body><div class='box'>" +
                "<h2>Security Check</h2>" +
                "<p>Please verify you are human to continue</p>" +
                "<form action='/verify' method='POST'>" +
                "<input type='hidden' name='token' value='" + token + "'>" +
                "<div class='g-recaptcha' data-sitekey='" + key + "'></div><br>" +
                "<button type='submit'>Verify and Continue</button>" +
                "</form></div></body></html>";

            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] resp = html.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        }
    }

    class VerifyHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String token = null;
            String recaptcha = "";

            for (String p : body.split("&")) {
                String[] kv = p.split("=", 2);
                if (kv.length == 2) {
                    if (kv[0].equals("token")) token = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    if (kv[0].equals("g-recaptcha-response")) recaptcha = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }

            boolean ok = captchaManager.verifyCaptcha(token, recaptcha);

            if (ok) {
                captchaManager.markSuccess(token);
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Success</title>" +
                    "<style>body{background:#d4edda;display:flex;justify-content:center;" +
                    "align-items:center;height:100vh;font-family:sans-serif;margin:0;}" +
                    ".box{background:white;padding:40px;border-radius:20px;text-align:center;" +
                    "box-shadow:0 10px 40px rgba(0,0,0,0.2);}" +
                    "h1{font-size:64px;margin:0;}h2{color:#155724;}" +
                    "</style></head><body><div class='box'>" +
                    "<h1>OK</h1><h2>Verification Passed!</h2>" +
                    "<p>You can now return to the game</p>" +
                    "</div></body></html>";

                ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            } else {
                captchaManager.markFailed(token);
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Failed</title>" +
                    "<style>body{background:#f8d7da;display:flex;justify-content:center;" +
                    "align-items:center;height:100vh;font-family:sans-serif;margin:0;}" +
                    ".box{background:white;padding:40px;border-radius:20px;text-align:center;" +
                    "box-shadow:0 10px 40px rgba(0,0,0,0.2);}" +
                    "h1{font-size:64px;margin:0;}h2{color:#721c24;}" +
                    "a{display:inline-block;margin-top:20px;padding:10px 20px;background:#007bff;" +
                    "color:white;text-decoration:none;border-radius:10px;}" +
                    "</style></head><body><div class='box'>" +
                    "<h1>ERR</h1><h2>Verification Failed</h2>" +
                    "<a href='/captcha?token=" + token + "'>Try Again</a>" +
                    "</div></body></html>";

                ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            }
        }
    }
}
