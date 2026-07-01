package com.limbocaptcha.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {

    private final Path configFile;
    private String siteKey, secretKey, captchaMessage, successMessage, kickMessage, failKickMessage, captchaUrlFormat, targetServer;
    private String mysqlHost, mysqlDatabase, mysqlUser, mysqlPassword;
    private int webPort, apiPort, captchaTimeout, mysqlPort;

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
            captchaTimeout = Integer.parseInt(p.getProperty("recaptcha.timeout-minutes", "5"));
            captchaMessage = p.getProperty("messages.captcha-message", "&ePass captcha:");
            successMessage = p.getProperty("messages.success-message", "&aOK!");
            kickMessage = p.getProperty("messages.kick-message", "&cTimeout!");
            failKickMessage = p.getProperty("messages.fail-kick-message", "&cFailed!");
            captchaUrlFormat = p.getProperty("recaptcha.url-format", "https://site.com/captcha.html?token=${token}");
            targetServer = p.getProperty("settings.target-server", "lobby");
            
            mysqlHost = p.getProperty("mysql.host", "localhost");
            mysqlPort = Integer.parseInt(p.getProperty("mysql.port", "3306"));
            mysqlDatabase = p.getProperty("mysql.database", "limbocaptcha");
            mysqlUser = p.getProperty("mysql.user", "root");
            mysqlPassword = p.getProperty("mysql.password", "");
        } catch (IOException e) { setDefaults(); }
    }

    private void createDefaultConfig() throws IOException {
        String config = 
            "recaptcha:\n" +
            "  site-key: \"YOUR_KEY\"\n" +
            "  secret-key: \"YOUR_SECRET\"\n" +
            "  url-format: \"https://site.com/captcha.html?token=${token}\"\n" +
            "  timeout-minutes: 5\n" +
            "mysql:\n" +
            "  host: \"localhost\"\n" +
            "  port: 3306\n" +
            "  database: \"limbocaptcha\"\n" +
            "  user: \"root\"\n" +
            "  password: \"\"\n" +
            "settings:\n" +
            "  target-server: \"lobby\"\n" +
            "messages:\n" +
            "  captcha-message: \"&ePass captcha:\"\n" +
            "  success-message: \"&aOK!\"\n" +
            "  kick-message: \"&cTimeout!\"\n" +
            "  fail-kick-message: \"&cFailed!\"\n";
        Files.writeString(configFile, config);
    }

    private void setDefaults() {
        siteKey = ""; secretKey = ""; captchaTimeout = 5;
        captchaMessage = "&ePass:"; successMessage = "&aOK!"; kickMessage = "&cTimeout!"; failKickMessage = "&cFailed!";
        captchaUrlFormat = "https://site.com/captcha.html?token=${token}"; targetServer = "lobby";
        mysqlHost = "localhost"; mysqlPort = 3306; mysqlDatabase = "limbocaptcha"; mysqlUser = "root"; mysqlPassword = "";
    }

    public String getSiteKey() { return siteKey; }
    public String getSecretKey() { return secretKey; }
    public int getCaptchaTimeout() { return captchaTimeout; }
    public String getCaptchaMessage() { return captchaMessage; }
    public String getSuccessMessage() { return successMessage; }
    public String getKickMessage() { return kickMessage; }
    public String getFailKickMessage() { return failKickMessage; }
    public String getTargetServer() { return targetServer; }
    public String getCaptchaUrl(String token) { return captchaUrlFormat.replace("${token}", token); }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUser() { return mysqlUser; }
    public String getMysqlPassword() { return mysqlPassword; }
    // Совместимость
    public int getWebPort() { return 8080; }
    public int getApiPort() { return 8081; }
}
