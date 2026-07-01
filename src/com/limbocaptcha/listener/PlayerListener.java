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

        passed.remove(uuid);
        pending.add(uuid);

        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> sendCaptcha(player))
            .delay(1, TimeUnit.SECONDS)
            .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        passed.remove(uuid);
        pending.remove(uuid);
        plugin.getCaptchaManager().cancelVerification(uuid);
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!passed.contains(uuid)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            if (!pending.contains(uuid)) sendCaptcha(event.getPlayer());
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        if (!passed.contains(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Component.text("Пройдите проверку перед использованием чата!", NamedTextColor.RED));
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        if (!passed.contains(player.getUniqueId())) {
            plugin.getServer().getScheduler()
                .buildTask(plugin, () -> player.disconnect(
                    Component.text(plugin.getConfigManager().getKickMessage().replace("&", "§"))))
                .delay(100, TimeUnit.MILLISECONDS)
                .schedule();
        }
    }

    private void sendCaptcha(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) return;
        pending.add(uuid);

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String token = plugin.getCaptchaManager().createToken(uuid, player.getUsername(), ip);
        String url = plugin.getConfigManager().getCaptchaUrl(token);
        String msg = plugin.getConfigManager().getCaptchaMessage().replace("&", "§");

        plugin.getLogger().info("[CAPTCHA] {} [{}] - Token: {} - IP: {}", player.getUsername(), uuid, token, ip);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " минут.", NamedTextColor.GRAY));

        plugin.getCaptchaManager().requestVerification(uuid)
            .orTimeout(plugin.getConfigManager().getCaptchaTimeout(), TimeUnit.MINUTES)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    String ok = plugin.getConfigManager().getSuccessMessage().replace("&", "§");
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text(ok, NamedTextColor.GREEN));
                    player.sendMessage(Component.text("§7Добро пожаловать на §6§lFakeWorld§7!", NamedTextColor.GREEN));
                    plugin.getLogger().info("[CAPTCHA] {} PASSED verification!", player.getUsername());

                    Optional<RegisteredServer> target = plugin.getServer()
                        .getServer(plugin.getConfigManager().getTargetServer());
                    if (target.isPresent()) {
                        player.createConnectionRequest(target.get()).connect();
                        player.sendMessage(Component.text("§7Подключаем к серверу...", NamedTextColor.GRAY));
                    } else {
                        player.sendMessage(Component.text("§7Используйте §e/server §7для подключения", NamedTextColor.GRAY));
                    }
                } else {
                    plugin.getLogger().info("[CAPTCHA] {} FAILED verification!", player.getUsername());
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§c§lПроверка не пройдена!", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7Перезайдите в игру чтобы попробовать снова.", NamedTextColor.GRAY));
                    passed.remove(uuid);
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                plugin.getLogger().info("[CAPTCHA] {} TIMEOUT!", player.getUsername());
                player.disconnect(Component.text(plugin.getConfigManager().getKickMessage().replace("&", "§")));
                return null;
            });
    }
}
