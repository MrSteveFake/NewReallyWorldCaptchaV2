package com.limbocaptcha.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {

    private final Path configFile;
    private String siteKey, secretKey, captchaMessage, successMessage, kickMessage, failKickMessage, captchaUrlFormat, targetServer;
    private int webPort, apiPort, captchaTimeout;

    public ConfigManager(Path dataDirectory) {
        configFile = dataDirectory.resolve("config.yml");
        loadConfig();
    }

    public void loadConfig() {
        try {
            if (!Files.exists(configFile)) { Files.createDirectories(configFile.getParent()); createDefaultConfig(); }
            Properties p = new Properties();
            BufferedReader r = Files.newBufferedReader(configFile);
            String line, section = "";
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.endsWith(":")) { section = line.substring(0, line.length()-1).trim(); continue; }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) p.setProperty(section + "." + parts[0].trim(), parts[1].trim().replace("\"", ""));
            }
            r.close();

            siteKey = p.getProperty("recaptcha.site-key", "");
            secretKey = p.getProperty("recaptcha.secret-key", "");
            webPort = Integer.parseInt(p.getProperty("webserver.port", "8080"));
            apiPort = Integer.parseInt(p.getProperty("webserver.api-port", "8081"));
            captchaTimeout = Integer.parseInt(p.getProperty("recaptcha.timeout-minutes", "5"));
            captchaMessage = p.getProperty("messages.captcha-message", "&ePass captcha:");
            successMessage = p.getProperty("messages.success-message", "&aOK!");
            kickMessage = p.getProperty("messages.kick-message", "&cTimeout!");
            failKickMessage = p.getProperty("messages.fail-kick-message", "&cFailed!");
            captchaUrlFormat = p.getProperty("recaptcha.url-format", "https://site.com/captcha.html?token=${token}");
            targetServer = p.getProperty("settings.target-server", "lobby");
        } catch (IOException e) { setDefaults(); }
    }

    private void createDefaultConfig() throws IOException {
        Files.writeString(configFile,
            "recaptcha:\n" +
            "  site-key: \"KEY\"\n" +
            "  secret-key: \"SECRET\"\n" +
            "  url-format: \"https://site.com/captcha.html?token=${token}\"\n" +
            "  timeout-minutes: 5\n" +
            "webserver:\n" +
            "  port: 8080\n" +
            "  api-port: 8081\n" +
            "settings:\n" +
            "  target-server: \"lobby\"\n" +
            "messages:\n" +
            "  captcha-message: \"&ePass captcha:\"\n" +
            "  success-message: \"&aOK!\"\n" +
            "  kick-message: \"&cTimeout!\"\n" +
            "  fail-kick-message: \"&cFailed!\"\n"
        );
    }

    private void setDefaults() {
        siteKey = ""; secretKey = ""; webPort = 8080; apiPort = 8081; captchaTimeout = 5;
        captchaMessage = "&ePass:"; successMessage = "&aOK!"; kickMessage = "&cTimeout!"; failKickMessage = "&cFailed!";
        captchaUrlFormat = "https://site.com/captcha.html?token=${token}"; targetServer = "lobby";
    }

    public String getSiteKey() { return siteKey; }
    public String getSecretKey() { return secretKey; }
    public int getWebPort() { return webPort; }
    public int getApiPort() { return apiPort; }
    public int getCaptchaTimeout() { return captchaTimeout; }
    public String getCaptchaMessage() { return captchaMessage; }
    public String getSuccessMessage() { return successMessage; }
    public String getKickMessage() { return kickMessage; }
    public String getFailKickMessage() { return failKickMessage; }
    public String getTargetServer() { return targetServer; }
    public String getCaptchaUrl(String token) { return captchaUrlFormat.replace("${token}", token); }
}
