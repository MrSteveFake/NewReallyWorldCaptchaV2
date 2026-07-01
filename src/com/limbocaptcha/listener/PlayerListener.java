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

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getLogger().info("[CAPTCHA] {} connected", player.getUsername());

        passed.remove(uuid);
        pending.add(uuid);

        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> sendCaptcha(player))
            .delay(500, TimeUnit.MILLISECONDS)
            .schedule();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Если капча пройдена - разрешаем ВСЕ подключения
        if (passed.contains(uuid)) {
            return;
        }

        // Проверяем, может это сервер LimboAuth?
        String serverName = event.getOriginalServer().getServerInfo().getName();
        
        // Если капча не пройдена и это НЕ LimboAuth сервер - блокируем
        if (!serverName.toLowerCase().contains("limbo") && !serverName.toLowerCase().contains("auth")) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            plugin.getLogger().info("[CAPTCHA] {} blocked from: {}", player.getUsername(), serverName);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChat(PlayerChatEvent event) {
        if (!passed.contains(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Component.text(
                "§cПройдите капчу перед использованием чата!",
                NamedTextColor.RED
            ));
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();

        // Если игрок не прошел капчу и подключился НЕ к LimboAuth - кикаем
        if (!passed.contains(uuid) && !serverName.toLowerCase().contains("limbo") && !serverName.toLowerCase().contains("auth")) {
            plugin.getLogger().warn("[CAPTCHA] {} connected to {} without captcha!", player.getUsername(), serverName);
            plugin.getServer().getScheduler()
                .buildTask(plugin, () -> player.disconnect(Component.text("§cПройдите капчу!")))
                .delay(50, TimeUnit.MILLISECONDS)
                .schedule();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        plugin.getCaptchaManager().cancelVerification(uuid);
    }

    private void sendCaptcha(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) return;
        pending.add(uuid);

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String token = plugin.getCaptchaManager().createToken(uuid, player.getUsername(), ip);
        String url = plugin.getConfigManager().getCaptchaUrl(token);

        plugin.getLogger().info("[CAPTCHA] {} | Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§eПройдите проверку по ссылке:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " мин.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7После прохождения вы попадете на авторизацию.", NamedTextColor.GRAY));

        plugin.getCaptchaManager().requestVerification(uuid)
            .orTimeout(plugin.getConfigManager().getCaptchaTimeout(), TimeUnit.MINUTES)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§a§l✅ Проверка пройдена!", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("§7Перемещаем на авторизацию...", NamedTextColor.GREEN));
                    
                    plugin.getLogger().info("[CAPTCHA] {} PASSED! Moving to LimboAuth...", player.getUsername());

                    // Пробуем найти LimboAuth сервер и подключить
                    Optional<RegisteredServer> limboServer = findLimboServer();
                    if (limboServer.isPresent()) {
                        player.createConnectionRequest(limboServer.get()).connectWithIndication();
                        plugin.getLogger().info("[CAPTCHA] {} connected to LimboAuth: {}", 
                            player.getUsername(), limboServer.get().getServerInfo().getName());
                    } else {
                        plugin.getLogger().warn("[CAPTCHA] LimboAuth server not found!");
                        player.sendMessage(Component.text("§7Используйте §e/server §7для подключения.", NamedTextColor.GREEN));
                    }
                } else {
                    plugin.getLogger().info("[CAPTCHA] {} FAILED/TIMEOUT", player.getUsername());
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§cСсылка устарела или проверка не пройдена.", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7Перезайдите в игру для получения новой ссылки.", NamedTextColor.GRAY));
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                plugin.getLogger().error("[CAPTCHA] {} error: {}", player.getUsername(), t.getMessage());
                return null;
            });
    }

    // Поиск сервера LimboAuth
    private Optional<RegisteredServer> findLimboServer() {
        // Ищем сервер с "limbo" или "auth" в названии
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName().toLowerCase();
            if (name.contains("limbo") || name.contains("auth")) {
                return Optional.of(server);
            }
        }
        // Если не нашли - берем первый доступный
        return plugin.getServer().getAllServers().stream().findFirst();
    }
}
