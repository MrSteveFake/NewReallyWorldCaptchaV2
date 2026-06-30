package com.limbocaptcha.captcha;

import com.limbocaptcha.config.ConfigManager;
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
    private final CaptchaManager captchaManager;
    private HttpServer server;

    public WebServer(ConfigManager configManager, CaptchaManager captchaManager) {
        this.configManager = configManager;
        this.captchaManager = captchaManager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(configManager.getWebPort()), 0);
            server.createContext("/captcha.html", new PageHandler());
            server.createContext("/verify", new VerifyHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("[LimboCaptcha] Web server started on port " + configManager.getWebPort());
        } catch (IOException e) {
            System.err.println("[LimboCaptcha] WebServer error: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[LimboCaptcha] Web server stopped");
        }
    }

    class PageHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String key = configManager.getSiteKey();
            String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
                "<title>LimboCaptcha</title>" +
                "<script src='https://www.google.com/recaptcha/api.js' async defer></script>" +
                "<style>body{display:flex;justify-content:center;align-items:center;height:100vh;" +
                "background:linear-gradient(135deg,#1e3c72,#2a5298);font-family:sans-serif;margin:0;}" +
                ".box{background:white;padding:40px;border-radius:20px;text-align:center;max-width:400px;}" +
                "h2{color:#333;}p{color:#666;}" +
                "button{background:#1e3c72;color:white;border:none;padding:12px 30px;" +
                "border-radius:25px;font-size:16px;cursor:pointer;margin-top:15px;}" +
                "</style></head><body><div class='box'>" +
                "<h2>Bot Protection</h2><p>Please verify you are human</p>" +
                "<form action='/verify' method='POST'><div class='g-recaptcha' data-sitekey='" + key + "'>" +
                "</div><br><button type='submit'>Verify</button></form>" +
                "</div></body></html>";

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
            String recaptcha = "";
            for (String p : body.split("&")) {
                String[] kv = p.split("=", 2);
                if (kv.length == 2 && kv[0].equals("g-recaptcha-response")) {
                    recaptcha = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }

            boolean ok = captchaManager.verifyCaptcha(recaptcha);

            String html = ok ?
                "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>OK</title>" +
                "<style>body{background:#d4edda;display:flex;justify-content:center;" +
                "align-items:center;height:100vh;font-family:sans-serif;}" +
                ".box{background:white;padding:40px;border-radius:20px;text-align:center;}" +
                "h1{font-size:64px;}h2{color:#155724;}</style></head><body>" +
                "<div class='box'><h1>OK</h1><h2>Verification passed!</h2></div></body></html>"
                :
                "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Error</title>" +
                "<style>body{background:#f8d7da;display:flex;justify-content:center;" +
                "align-items:center;height:100vh;font-family:sans-serif;}" +
                ".box{background:white;padding:40px;border-radius:20px;text-align:center;}" +
                "h1{font-size:64px;}h2{color:#721c24;}" +
                "a{display:inline-block;margin-top:20px;padding:10px 20px;background:#007bff;" +
                "color:white;text-decoration:none;border-radius:10px;}" +
                "</style></head><body><div class='box'>" +
                "<h1>ERR</h1><h2>Error</h2><a href='/captcha.html'>Try again</a></div></body></html>";

            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] resp = html.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        }
    }
}
