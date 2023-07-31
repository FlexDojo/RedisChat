package dev.unnm3d.redischat.commands;

import dev.unnm3d.redischat.RedisChat;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import dev.unnm3d.redischat.integrations.VanishIntegration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.metadevs.redistab.api.RedisTabAPI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListManager {
    private final BukkitTask task;
    private final ConcurrentHashMap<String, Long> playerList;
    private final List<VanishIntegration> vanishIntegrations;
    private final RedisChat plugin;
    private RedisTabAPI redisTabAPI;

    public PlayerListManager(RedisChat plugin) {
        this.playerList = new ConcurrentHashMap<>();
        this.vanishIntegrations = new ArrayList<>();
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                playerList.entrySet().removeIf(stringLongEntry -> System.currentTimeMillis() - stringLongEntry.getValue() > 1000 * 6);

                List<String> tempList = new ArrayList<>();
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(player -> vanishIntegrations.stream().noneMatch(integration -> integration.isVanished(player)))
                        .map(HumanEntity::getName)
                        .filter(s -> !s.isEmpty())
                        .forEach(tempList::add);
                plugin.getRedisDataManager().publishPlayerList(tempList);

                tempList.forEach(s -> playerList.put(s, System.currentTimeMillis()));
            }
        }.runTaskTimerAsynchronously(plugin, 0, 100);//5 seconds
        }.runTaskTimerAsynchronously(plugin, 0, 200);//10 seconds
        listenPlayerListUpdate();
        loadRedisTabAPI();
    }

    private void loadRedisTabAPI() {
        if (Bukkit.getPluginManager().isPluginEnabled("RedisTab")) {
            redisTabAPI = Bukkit.getServicesManager().load(RedisTabAPI.class);
        }
    }

    public void updatePlayerList(List<String> inPlayerList) {
        long currentTimeMillis = System.currentTimeMillis();
        inPlayerList.forEach(s -> {
            if (s != null && !s.isEmpty())
                playerList.put(s, currentTimeMillis);
        });
    }

    public void addVanishIntegration(VanishIntegration vanishIntegration) {
        vanishIntegrations.add(vanishIntegration);
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
