package com.limbocaptcha.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {

    private final Path configFile;
    private String siteKey;
    private String secretKey;
    private int webPort;
    private int captchaTimeout;
    private String captchaMessage;
    private String successMessage;
    private String kickMessage;
    private String captchaUrlFormat;

    public ConfigManager(Path dataDirectory) {
        this.configFile = dataDirectory.resolve("config.yml");
        loadConfig();
    }

    public void loadConfig() {
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                createDefaultConfig();
            }

            Properties props = new Properties();
            BufferedReader reader = Files.newBufferedReader(configFile);
            String line;
            String section = "";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.endsWith(":")) {
                    section = line.substring(0, line.length() - 1).trim();
                    continue;
                }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    props.setProperty(section + "." + parts[0].trim(), parts[1].trim().replace("\"", ""));
                }
            }
            reader.close();

            siteKey = props.getProperty("recaptcha.site-key", "your-site-key-here");
            secretKey = props.getProperty("recaptcha.secret-key", "your-secret-key-here");
            webPort = Integer.parseInt(props.getProperty("webserver.port", "8080"));
            captchaTimeout = Integer.parseInt(props.getProperty("recaptcha.timeout-minutes", "5"));
            captchaMessage = props.getProperty("messages.captcha-message", "&eПройдите капчу:");
            successMessage = props.getProperty("messages.success-message", "&aУспешно!");
            kickMessage = props.getProperty("messages.kick-message", "&cВремя истекло!");
            captchaUrlFormat = props.getProperty("recaptcha.url-format", "http://localhost:${port}/captcha.html");

        } catch (IOException e) {
            setDefaults();
        }
    }

    private void createDefaultConfig() throws IOException {
        String config = "recaptcha:\n" +
            "  site-key: \"your-site-key-here\"\n" +
            "  secret-key: \"your-secret-key-here\"\n" +
            "  url-format: \"http://localhost:${port}/captcha.html\"\n" +
            "  timeout-minutes: 5\n" +
            "webserver:\n" +
            "  port: 8080\n" +
            "messages:\n" +
            "  captcha-message: \"&eПожалуйста, пройдите капчу:\"\n" +
            "  success-message: \"&aКапча пройдена!\"\n" +
            "  kick-message: \"&cВремя истекло!\"\n";
        Files.writeString(configFile, config);
    }

    private void setDefaults() {
        siteKey = "your-site-key-here";
        secretKey = "your-secret-key-here";
        webPort = 8080;
        captchaTimeout = 5;
        captchaMessage = "&eПройдите капчу:";
        successMessage = "&aУспешно!";
        kickMessage = "&cВремя истекло!";
        captchaUrlFormat = "http://localhost:${port}/captcha.html";
    }

    public String getSiteKey() { return siteKey; }
    public String getSecretKey() { return secretKey; }
    public int getWebPort() { return webPort; }
    public int getCaptchaTimeout() { return captchaTimeout; }
    public String getCaptchaMessage() { return captchaMessage; }
    public String getSuccessMessage() { return successMessage; }
    public String getKickMessage() { return kickMessage; }
    public String getCaptchaUrl() { return captchaUrlFormat.replace("${port}", String.valueOf(webPort)); }
}
