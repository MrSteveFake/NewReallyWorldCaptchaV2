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
    // Убираем BAN систему полностью

    public PlayerListener(LimboCaptcha plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Сбрасываем статусы при входе
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
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        if (!passed.contains(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Component.text("Пройдите проверку капчи!", NamedTextColor.RED));
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

        plugin.getLogger().info("[CAPTCHA] {} | Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " мин.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7Если ссылка истекла — перезайдите для получения новой.", NamedTextColor.GRAY));

        // Запускаем проверку с таймаутом, но НЕ КИКАЕМ
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
                    plugin.getLogger().info("[CAPTCHA] {} PASSED!", player.getUsername());

                    // Подключаем к серверу
                    Optional<RegisteredServer> target = plugin.getServer()
                        .getServer(plugin.getConfigManager().getTargetServer());
                    if (target.isPresent()) {
                        player.createConnectionRequest(target.get()).connect();
                    } else {
                        player.sendMessage(Component.text("§7Используйте §e/server §7для подключения", NamedTextColor.GRAY));
                    }
                } else {
                    // НЕ КИКАЕМ! Просто отправляем сообщение
                    plugin.getLogger().info("[CAPTCHA] {} FAILED or TIMEOUT", player.getUsername());
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§cСсылка устарела или проверка не пройдена.", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7Перезайдите в игру чтобы получить новую ссылку.", NamedTextColor.GRAY));
                    player.sendMessage(Component.text(""));
                    // Не кикаем, игрок может перезайти сам
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                plugin.getLogger().info("[CAPTCHA] {} ERROR: {}", player.getUsername(), t.getMessage());
                // НЕ КИКАЕМ при ошибке
                return null;
            });
    }
}
