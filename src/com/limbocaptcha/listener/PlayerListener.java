package com.limbocaptcha.listener;

import com.limbocaptcha.LimboCaptcha;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

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
            event.getPlayer().sendMessage(Component.text("Пройдите капчу!", NamedTextColor.RED));
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

        String url = plugin.getConfigManager().getCaptchaUrl();
        String msg = plugin.getConfigManager().getCaptchaMessage().replace("&", "§");
        String ok = plugin.getConfigManager().getSuccessMessage().replace("&", "§");

        player.sendMessage(Component.text("==============================", NamedTextColor.GOLD));
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text("==============================", NamedTextColor.GOLD));

        plugin.getCaptchaManager().requestVerification(uuid)
            .orTimeout(plugin.getConfigManager().getCaptchaTimeout(), TimeUnit.MINUTES)
            .thenAccept(success -> {
                pending.remove(uuid);
                if (success) {
                    passed.add(uuid);
                    player.sendMessage(Component.text(ok, NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Можете играть! /server", NamedTextColor.GREEN));
                } else {
                    kick(player);
                }
            })
            .exceptionally(t -> { pending.remove(uuid); kick(player); return null; });
    }

    private void kick(Player player) {
        player.disconnect(Component.text(plugin.getConfigManager().getKickMessage().replace("&", "§")));
        passed.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
    }
}
