package dev.unnm3d.redischat.channels;

import dev.unnm3d.redischat.Permissions;
import dev.unnm3d.redischat.RedisChat;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.Map;

public class PlayerChannel extends AbstractItem {
    @Getter
    private final Channel channel;
    /**
     * 0 = active, not listening
     * 1 = active, listening
     * -1 = active, muted
     */
    private int status;


    public PlayerChannel(Channel channel, int status) {
        this.channel = channel;
        this.status = status;
    }

    public boolean isListening() {
        return status == 1;
    }

    public boolean isMuted() {
        return status == -1;
    }


    @Override
    public ItemProvider getItemProvider() {
        ItemStack item;
        if (status == -1)
            item = RedisChat.getInstance().guiSettings.mutedChannel;
        else if (status == 1)
            item = RedisChat.getInstance().guiSettings.activeChannelButton;
        else
            item = RedisChat.getInstance().guiSettings.idleChannel;

        ItemMeta im = item.getItemMeta();
        if (im != null)
            im.setDisplayName("§r" + channel.getName());
        item.setItemMeta(im);
        return new ItemBuilder(item);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        if (clickType.isLeftClick()) {
            if (status == 0) {
                status = 1;
                RedisChat.getInstance().getDataManager().setPlayerChannelStatuses(player.getName(), Map.of(channel.getName(), "1"));
            } else if (status == 1) {
                status = 0;
                RedisChat.getInstance().getDataManager().setPlayerChannelStatuses(player.getName(), Map.of(channel.getName(), "0"));
            }
        } else if (clickType.isRightClick()) {
            if (status == 0) {
                RedisChat.getInstance().getDataManager().setPlayerChannelStatuses(player.getName(), Map.of(channel.getName(), "-1"));
                RedisChat.getInstance().getPermissionProvider().unsetPermission(player, Permissions.CHANNEL_PREFIX.getPermission() + channel.getName());
                status = -1;
            } else if (status == -1) {
                RedisChat.getInstance().getDataManager().setPlayerChannelStatuses(player.getName(), Map.of(channel.getName(), "0"));
                RedisChat.getInstance().getPermissionProvider().setPermission(player, Permissions.CHANNEL_PREFIX.getPermission() + channel.getName());
                status = 0;
            }
        }
        notifyWindows();
    }
}
