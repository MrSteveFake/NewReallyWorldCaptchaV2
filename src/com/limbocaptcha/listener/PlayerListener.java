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
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerListener {

    private final LimboCaptcha plugin;
    private final Set<UUID> passed = new ConcurrentHashMap<UUID, Boolean>().newKeySet();
    private final Set<UUID> pending = new ConcurrentHashMap<UUID, Boolean>().newKeySet();
    private final Map<UUID, ScheduledTask> keepAliveTasks = new ConcurrentHashMap<>();

    public PlayerListener(LimboCaptcha plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.getLogger().info("[CAPTCHA] {} connected", player.getUsername());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();

        if (passed.contains(uuid)) return;

        if (!serverName.toLowerCase().contains("limbo") && !serverName.toLowerCase().contains("auth")) {
            if (!pending.contains(uuid)) {
                sendCaptcha(player);
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getOriginalServer().getServerInfo().getName();

        if (serverName.toLowerCase().contains("limbo") || serverName.toLowerCase().contains("auth")) {
            return;
        }

        if (!passed.contains(uuid)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            if (!pending.contains(uuid)) sendCaptcha(player);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        stopKeepAlive(uuid);
    }

    private void sendCaptcha(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isActive()) return;
        pending.add(uuid);

        startKeepAlive(player);

        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        String token = plugin.getCaptchaManager().createToken(uuid, player.getUsername(), ip);
        String url = plugin.getConfigManager().getCaptchaUrl(token);

        plugin.getLogger().info("[CAPTCHA] Player: {} Token: {}", player.getUsername(), token);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§eПройдите проверку по ссылке:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7После прохождения подождите 3 секунды...", NamedTextColor.GRAY));

        // Запускаем опрос БД каждые 2 секунды
        startPolling(player, token);
    }

    private void startPolling(Player player, String token) {
        UUID uuid = player.getUniqueId();
        
        ScheduledTask pollTask = plugin.getServer().getScheduler()
            .buildTask(plugin, () -> {
                if (!player.isActive() || !pending.contains(uuid)) {
                    return;
                }

                // Проверяем статус в БД
                String status = plugin.getDatabaseManager().getTokenStatus(token);
                
                if ("success".equals(status)) {
                    pending.remove(uuid);
                    stopKeepAlive(uuid);
                    passed.add(uuid);
                    
                    plugin.getLogger().info("[CAPTCHA] {} PASSED! (polling)", player.getUsername());
                    
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("§a§l✅ Проверка пройдена!", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("§7Добро пожаловать! Используйте §e/server §7для игры.", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(""));
                    
                    // Отменяем этот опрос
                    throw new RuntimeException("STOP_POLLING");
                }
            })
            .repeat(2, TimeUnit.SECONDS)
            .schedule();
    }

    private void startKeepAlive(Player player) {
        UUID uuid = player.getUniqueId();
        stopKeepAlive(uuid);

        ScheduledTask task = plugin.getServer().getScheduler()
            .buildTask(plugin, () -> {
                if (player.isActive() && pending.contains(uuid)) {
                    player.sendMessage(Component.text("§7⏳ Ожидание капчи...", NamedTextColor.DARK_GRAY));
                }
            })
            .repeat(15, TimeUnit.SECONDS)
            .schedule();

        keepAliveTasks.put(uuid, task);
    }

    private void stopKeepAlive(UUID uuid) {
        ScheduledTask task = keepAliveTasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
