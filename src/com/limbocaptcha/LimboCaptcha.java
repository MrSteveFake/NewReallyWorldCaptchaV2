package com.limbocaptcha;

import com.limbocaptcha.api.ApiServer;
import com.limbocaptcha.captcha.CaptchaManager;
import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.database.DatabaseManager;
import com.limbocaptcha.listener.PlayerListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import java.nio.file.Path;

@Plugin(id = "limbocaptcha", name = "LimboCaptcha", version = "1.0.0", authors = {"KondrMS"})
public class LimboCaptcha {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CaptchaManager captchaManager;
    private ApiServer apiServer;
    private static LimboCaptcha instance;

    @Inject
    public LimboCaptcha(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        configManager = new ConfigManager(dataDirectory);
        databaseManager = new DatabaseManager(dataDirectory);
        captchaManager = new CaptchaManager(configManager, databaseManager);
        apiServer = new ApiServer(configManager, captchaManager, databaseManager);
        apiServer.start();
        server.getEventManager().register(this, new PlayerListener(this));
        logger.info("LimboCaptcha loaded! Ports: Web={}, API={}", configManager.getWebPort(), configManager.getApiPort());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (apiServer != null) apiServer.stop();
        if (captchaManager != null) captchaManager.shutdown();
        if (databaseManager != null) databaseManager.close();
    }

    public static LimboCaptcha getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }
    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
}
