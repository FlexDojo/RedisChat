package dev.unnm3d.redischat.commands;

import dev.unnm3d.redischat.RedisChat;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.metadevs.redistab.api.RedisTabAPI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.unnm3d.redischat.redis.redistools.RedisKeys.PLAYERLIST;

public class PlayerListManager {
    private final BukkitTask task;
    private final ConcurrentHashMap<String, Long> playerList;
    private final RedisChat plugin;
    private RedisTabAPI redisTabAPI;

    public PlayerListManager(RedisChat plugin) {
        this.plugin = plugin;
        this.playerList = new ConcurrentHashMap<>();
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                playerList.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 1000 * 11);
                plugin.getServer().getOnlinePlayers().stream()
                        .map(HumanEntity::getName)
                        .filter(s -> !s.isEmpty())
                        .forEach(name -> playerList.put(name, System.currentTimeMillis()));
                plugin.getRedisDataManager().getConnectionAsync(connection ->
                        connection.publish(PLAYERLIST.toString(),
                                String.join("§", playerList.keySet().stream().toList()))
                );
            }
        }.runTaskTimerAsynchronously(plugin, 0, 200);//10 seconds
        listenPlayerListUpdate();
        loadRedisTabAPI();
    }

    private void loadRedisTabAPI() {
        if (Bukkit.getPluginManager().isPluginEnabled("RedisTab")) {
            redisTabAPI = Bukkit.getServicesManager().load(RedisTabAPI.class);
        }
    }

    public void listenPlayerListUpdate() {
        StatefulRedisPubSubConnection<String, String> pubSubConnection = plugin.getRedisDataManager().getPubSubConnection();
        pubSubConnection.addListener(new RedisPubSubListener<>() {
            @Override
            public void message(String channel, String message) {
                Arrays.asList(message.split("§")).forEach(s -> {
                    if (s != null && !s.isEmpty())
                        playerList.put(s, System.currentTimeMillis());
                });

            }

            @Override
            public void message(String pattern, String channel, String message) {
            }

            @Override
            public void subscribed(String channel, long count) {
            }

            @Override
            public void psubscribed(String pattern, long count) {
            }

            @Override
            public void unsubscribed(String channel, long count) {
            }

            @Override
            public void punsubscribed(String pattern, long count) {
            }
        });
        pubSubConnection.async().subscribe(PLAYERLIST.toString())
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    plugin.getLogger().warning("Error subscribing to playerlist channel");
                    return null;
                })
                .thenAccept(subscription -> plugin.getLogger().info("Subscribed to channel: " + PLAYERLIST));
    }

    public boolean isNotVisible(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        if (redisTabAPI != null) {
            return !redisTabAPI.canSee(player.getUniqueId(), name);
        } else {
            return false;
        }
    }

    public Set<String> getPlayers(@Nullable CommandSender sender) {
        if (sender == null) {
            Set<String> players = new HashSet<>(redisTabAPI.getTotalPlayers());
            redisTabAPI.getVanishedPlayers().forEach(players::remove);
            return players;
        } else if (!(sender instanceof Player player)) {
            return new HashSet<>(redisTabAPI.getTotalPlayers());
        } else if (redisTabAPI.canSeeVanished(player.getUniqueId())) {
            return new HashSet<>(redisTabAPI.getTotalPlayers());
        } else {
            Set<String> players = new HashSet<>(redisTabAPI.getTotalPlayers());
            redisTabAPI.getVanishedPlayers().forEach(players::remove);
            return players;
        }
    }

    public void stop() {
        task.cancel();
    }

}
