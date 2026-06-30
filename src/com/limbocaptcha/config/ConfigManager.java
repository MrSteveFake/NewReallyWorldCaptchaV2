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
    private int apiPort;
    private int captchaTimeout;
    private String captchaMessage;
    private String successMessage;
    private String kickMessage;
    private String failKickMessage;
    private String captchaUrlFormat;
    private String targetServer;

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
            apiPort = Integer.parseInt(props.getProperty("webserver.api-port", "8081"));
            captchaTimeout = Integer.parseInt(props.getProperty("recaptcha.timeout-minutes", "5"));
            captchaMessage = props.getProperty("messages.captcha-message", "&ePlease pass captcha:");
            successMessage = props.getProperty("messages.success-message", "&aCaptcha passed!");
            kickMessage = props.getProperty("messages.kick-message", "&cCaptcha timeout!");
            failKickMessage = props.getProperty("messages.fail-kick-message", "&cCaptcha failed!");
            captchaUrlFormat = props.getProperty("recaptcha.url-format", "https://your-site.com/captcha?token=${token}");
            targetServer = props.getProperty("settings.target-server", "lobby");

        } catch (IOException e) {
            setDefaults();
        }
    }

    private void createDefaultConfig() throws IOException {
        String config = "# LimboCaptcha Configuration by KondrMS\n" +
            "recaptcha:\n" +
            "  site-key: \"your-site-key-here\"\n" +
            "  secret-key: \"your-secret-key-here\"\n" +
            "  url-format: \"https://your-site.com/captcha?token=${token}\"\n" +
            "  timeout-minutes: 5\n" +
            "webserver:\n" +
            "  port: 8080\n" +
            "  api-port: 8081\n" +
            "settings:\n" +
            "  target-server: \"lobby\"\n" +
            "messages:\n" +
            "  captcha-message: \"&ePlease pass captcha:\"\n" +
            "  success-message: \"&aCaptcha passed! Welcome!\"\n" +
            "  kick-message: \"&cCaptcha timeout!\"\n" +
            "  fail-kick-message: \"&cCaptcha verification failed!\"\n";
        Files.writeString(configFile, config);
    }

    private void setDefaults() {
        siteKey = "your-site-key-here";
        secretKey = "your-secret-key-here";
        webPort = 8080;
        apiPort = 8081;
        captchaTimeout = 5;
        captchaMessage = "&ePlease pass captcha:";
        successMessage = "&aCaptcha passed!";
        kickMessage = "&cCaptcha timeout!";
        failKickMessage = "&cCaptcha failed!";
        captchaUrlFormat = "https://your-site.com/captcha?token=${token}";
        targetServer = "lobby";
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
