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

        plugin.getLogger().info("[CAPTCHA] {} connected - sending captcha", player.getUsername());

        // Всегда отправляем капчу при входе
        passed.remove(uuid);
        pending.add(uuid);

        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> sendCaptcha(player))
            .delay(1, TimeUnit.SECONDS)
            .schedule();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Если прошел капчу - пропускаем везде
        if (passed.contains(uuid)) {
            return;
        }

        String serverName = event.getOriginalServer().getServerInfo().getName().toLowerCase();
        
        // Разрешаем только limbo/auth сервера
        if (!serverName.contains("limbo") && !serverName.contains("auth")) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChat(PlayerChatEvent event) {
        if (!passed.contains(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // НЕ сбрасываем passed - игрок остается верифицированным
        pending.remove(uuid);
    }

    private void sendCaptcha(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) return;
        pending.add(uuid);

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String token = plugin.getCaptchaManager().createToken(uuid, player.getUsername(), ip);
        String url = plugin.getConfigManager().getCaptchaUrl(token);

        plugin.getLogger().info("[CAPTCHA] SEND | Player: {} | Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§eПройдите проверку по ссылке:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " минут", NamedTextColor.GRAY));

        // НЕ ИСПОЛЬЗУЕМ orTimeout - он кикает!
        // Используем свой таймер
        plugin.getCaptchaManager().requestVerification(uuid)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    plugin.getLogger().info("[CAPTCHA] PASSED | Player: {} | Token: {}", player.getUsername(), token);

                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§a§l✅ Проверка пройдена!", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("§7Добро пожаловать на сервер!", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(""));

                    // Подключаем к limbo/auth серверу
                    connectToLimbo(player);
                } else {
                    plugin.getLogger().info("[CAPTCHA] FAILED | Player: {} | Token: {}", player.getUsername(), token);
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§cПроверка не пройдена.", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7Перезайдите для получения новой ссылки.", NamedTextColor.GRAY));
                    player.sendMessage(Component.text(""));
                }
            });
    }

    private void connectToLimbo(Player player) {
        // Ищем limbo сервер
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName().toLowerCase();
            if (name.contains("limbo") || name.contains("auth")) {
                player.createConnectionRequest(server).connectWithIndication();
                plugin.getLogger().info("[CAPTCHA] {} connecting to: {}", player.getUsername(), server.getServerInfo().getName());
                return;
            }
        }
        // Если не нашли - к первому доступному
        Optional<RegisteredServer> first = plugin.getServer().getAllServers().stream().findFirst();
        first.ifPresent(s -> player.createConnectionRequest(s).connectWithIndication());
    }
}
