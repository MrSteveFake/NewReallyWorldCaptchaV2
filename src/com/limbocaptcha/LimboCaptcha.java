package com.limbocaptcha;

import com.limbocaptcha.captcha.CaptchaManager;
import com.limbocaptcha.config.ConfigManager;
import com.limbocaptcha.listener.PlayerListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "limbocaptcha",
    name = "LimboCaptcha",
    version = "1.0.0",
    description = "Google reCaptcha v2 verification",
    authors = {"YourName"}
)
public class LimboCaptcha {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;
    private CaptchaManager captchaManager;
    private static LimboCaptcha instance;

    public LimboCaptcha(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.configManager = new ConfigManager(dataDirectory);
        this.captchaManager = new CaptchaManager(configManager);
        server.getEventManager().register(this, new PlayerListener(this));
        logger.info("LimboCaptcha загружен! Порт: {}", configManager.getWebPort());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (captchaManager != null) captchaManager.shutdown();
        logger.info("LimboCaptcha выключен!");
    }

    public static LimboCaptcha getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }
    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
}
