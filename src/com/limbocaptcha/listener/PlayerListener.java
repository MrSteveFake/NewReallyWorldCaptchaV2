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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerListener {

    private final LimboCaptcha plugin;
    private final Set<UUID> passed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> banned = new ConcurrentHashMap<>();
    private static final long BAN_TIME = TimeUnit.MINUTES.toMillis(5);

    public PlayerListener(LimboCaptcha plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Long banExpire = banned.get(uuid);
        if (banExpire != null) {
            if (System.currentTimeMillis() < banExpire) {
                long min = (banExpire - System.currentTimeMillis()) / 60000;
                player.disconnect(Component.text(
                    "§c§lВЫ ЗАБЛОКИРОВАНЫ!\n\n" +
                    "§7Причина: §fПодозрительная активность\n" +
                    "§7До разблокировки: §f" + min + " мин."
                ));
                return;
            }
            banned.remove(uuid);
        }

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
            event.getPlayer().sendMessage(Component.text("Пройдите проверку!", NamedTextColor.RED));
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

        plugin.getLogger().info("[CAPTCHA] {} Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " мин.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§c⚠ При провале — бан 5 минут!", NamedTextColor.RED));

        // Ждем прохождения капчи
        plugin.getCaptchaManager().requestVerification(uuid)
            .orTimeout(plugin.getConfigManager().getCaptchaTimeout(), TimeUnit.MINUTES)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    player.sendMessage(Component.text(plugin.getConfigManager().getSuccessMessage().replace("&", "§"), NamedTextColor.GREEN));
                    plugin.getLogger().info("[CAPTCHA] {} PASSED!", player.getUsername());
                    
                    Optional<RegisteredServer> target = plugin.getServer().getServer(plugin.getConfigManager().getTargetServer());
                    target.ifPresent(rs -> player.createConnectionRequest(rs).connect());
                } else {
                    banned.put(uuid, System.currentTimeMillis() + BAN_TIME);
                    plugin.getLogger().warn("[CAPTCHA] {} FAILED! BANNED!", player.getUsername());
                    player.disconnect(Component.text("§c§lПодозрительная активность!\n§7Бан на 5 минут."));
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                player.disconnect(Component.text("§cВремя истекло!"));
                return null;
            });
    }
}
