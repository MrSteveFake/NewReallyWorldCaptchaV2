package com.limbocaptcha.listener;

import com.limbocaptcha.LimboCaptcha;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerListener {

    private final LimboCaptcha plugin;
    private final Set<UUID> passed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public PlayerListener(LimboCaptcha plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Если уже прошел капчу - пропускаем
        if (passed.contains(uuid)) {
            return;
        }

        plugin.getLogger().info("[CAPTCHA] {} logged in - waiting for auth", player.getUsername());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();

        plugin.getLogger().info("[CAPTCHA] {} connected to: {}", player.getUsername(), serverName);

        // Если уже прошел капчу - пропускаем
        if (passed.contains(uuid)) {
            return;
        }

        // Если это НЕ limbo/auth сервер (значит авторизация пройдена) - показываем капчу
        if (!serverName.toLowerCase().contains("limbo") && !serverName.toLowerCase().contains("auth")) {
            if (!pending.contains(uuid)) {
                plugin.getLogger().info("[CAPTCHA] {} passed auth, sending captcha for server: {}", player.getUsername(), serverName);
                sendCaptcha(player);
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getOriginalServer().getServerInfo().getName();

        // Пропускаем limbo/auth сервера
        if (serverName.toLowerCase().contains("limbo") || serverName.toLowerCase().contains("auth")) {
            return;
        }

        // Если не прошел капчу - блокируем подключение к игровым серверам
        if (!passed.contains(uuid)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            plugin.getLogger().info("[CAPTCHA] {} BLOCKED from: {}", player.getUsername(), serverName);
            
            if (!pending.contains(uuid)) {
                sendCaptcha(player);
            }
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        if (!passed.contains(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Component.text("Пройдите капчу!", NamedTextColor.RED));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
    }

    private void sendCaptcha(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) return;
        pending.add(uuid);

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String token = plugin.getCaptchaManager().createToken(uuid, player.getUsername(), ip);
        String url = plugin.getConfigManager().getCaptchaUrl(token);

        plugin.getLogger().info("[CAPTCHA] SEND to {} | Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§eПройдите проверку по ссылке:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));

        // Просто запускаем проверку без таймаута
        plugin.getCaptchaManager().requestVerification(uuid)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    plugin.getLogger().info("[CAPTCHA] {} PASSED!", player.getUsername());

                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§a§l✅ Проверка пройдена!", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("§7Добро пожаловать! Используйте §e/server §7для игры.", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(""));
                }
            });
    }
}
