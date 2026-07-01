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
    private static final String BAN_REASON = "§c§lПодозрительная активность\n§7Повторите попытку через 5 минут";

    public PlayerListener(LimboCaptcha plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Проверка бана
        Long banExpire = banned.get(uuid);
        if (banExpire != null) {
            if (System.currentTimeMillis() < banExpire) {
                long remaining = (banExpire - System.currentTimeMillis()) / 1000 / 60;
                player.disconnect(Component.text(
                    "§c§lВы заблокированы!\n\n" +
                    "§7Причина: §fПодозрительная активность\n" +
                    "§7Разблокировка через: §f" + remaining + " мин.\n\n" +
                    "§7Пожалуйста, подождите."
                ));
                return;
            } else {
                banned.remove(uuid);
            }
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

        plugin.getLogger().info("[CAPTCHA] {} [{}] - Token: {} - IP: {}", 
            player.getUsername(), uuid, token, ip);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§lFakeWorld §7▸ §fПроверка безопасности", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§b§n" + url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7Ссылка действительна " + plugin.getConfigManager().getCaptchaTimeout() + " мин.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§c§lВнимание! §7При провале проверки вы будете заблокированы на 5 минут!", NamedTextColor.RED));

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
                    // БАН на 5 минут при провале капчи
                    banned.put(uuid, System.currentTimeMillis() + BAN_TIME);
                    plugin.getLogger().warn("[CAPTCHA] {} FAILED verification! BANNED for 5 minutes!", player.getUsername());
                    
                    player.disconnect(Component.text(
                        "§c§lВЫ ЗАБЛОКИРОВАНЫ!\n\n" +
                        "§7Причина: §fПодозрительная активность\n" +
                        "§7Длительность: §f5 минут\n\n" +
                        "§7Вы провалили проверку безопасности.\n" +
                        "§7Пожалуйста, подождите §f5 минут §7и зайдите снова."
                    ));
                    passed.remove(uuid);
                }
            })
            .exceptionally(t -> {
                pending.remove(uuid);
                plugin.getLogger().info("[CAPTCHA] {} TIMEOUT!", player.getUsername());
                player.disconnect(Component.text(
                    "§c§lВремя истекло!\n\n" +
                    "§7Вы не прошли проверку вовремя.\n" +
                    "§7Перезайдите чтобы получить новую ссылку."
                ));
                return null;
            });
    }
}
