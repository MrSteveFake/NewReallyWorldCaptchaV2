package com.limbocaptcha.listener;

import com.limbocaptcha.LimboCaptcha;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
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

        if (passed.contains(uuid)) return;
        pending.add(uuid);

        plugin.getServer().getScheduler()
            .buildTask(plugin, () -> sendCaptcha(player))
            .delay(1, TimeUnit.SECONDS)
            .schedule();
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
            event.getPlayer().sendMessage(Component.text("Pass captcha first!", NamedTextColor.RED));
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

        plugin.getLogger().info("[CAPTCHA] Player {} [{}] - Token: {} - IP: {}", 
            player.getUsername(), uuid, token, ip);

        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));

        plugin.getCaptchaManager().requestVerification(uuid)
            .orTimeout(plugin.getConfigManager().getCaptchaTimeout(), TimeUnit.MINUTES)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    String ok = plugin.getConfigManager().getSuccessMessage().replace("&", "§");
                    player.sendMessage(Component.text(ok, NamedTextColor.GREEN));
                    plugin.getLogger().info("[CAPTCHA] Player {} PASSED verification!", player.getUsername());

                    Optional<RegisteredServer> target = plugin.getServer()
                        .getServer(plugin.getConfigManager().getTargetServer());
                    if (target.isPresent()) {
                        player.createConnectionRequest(target.get()).connect();
                    } else {
                        player.sendMessage(Component.text("Use /server to connect", NamedTextColor.GREEN));
                    }
                } else {
                    plugin.getLogger().info("[CAPTCHA] Player {} FAILED verification!", player.getUsername());
                    kick(player, plugin.getConfigManager().getFailKickMessage().replace("&", "§"));
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                plugin.getLogger().info("[CAPTCHA] Player {} verification TIMEOUT!", player.getUsername());
                kick(player, plugin.getConfigManager().getKickMessage().replace("&", "§"));
                return null;
            });
    }

    private void kick(Player player, String message) {
        player.disconnect(Component.text(message));
        passed.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
    }
}
