package dev.unnm3d.redischat.channels;

import dev.unnm3d.redischat.Permissions;
import dev.unnm3d.redischat.RedisChat;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

public class MutePublic extends AbstractItem {

    private boolean muted;

    public MutePublic(boolean muted) {
        this.muted = muted;
    }

    @Override
    public ItemProvider getItemProvider() {
        ItemStack itemStack = muted ? RedisChat.getInstance().guiSettings.unSilencePublicButton : RedisChat.getInstance().guiSettings.silencePublicButton;
        return new ItemBuilder(itemStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        RedisChat plugin = RedisChat.getInstance();
        if (muted) {
            plugin.getPermissionProvider().setPermission(player, Permissions.CHANNEL_PUBLIC.getPermission());
            plugin.messages.sendMessage(player, plugin.messages.publicChannelUnmuted);
            muted = false;
        } else {
            plugin.getPermissionProvider().unsetPermission(player, Permissions.CHANNEL_PUBLIC.getPermission());
            plugin.messages.sendMessage(player, plugin.messages.publicChannelMuted);
            muted = true;
        }
        notifyWindows();
    }
}
