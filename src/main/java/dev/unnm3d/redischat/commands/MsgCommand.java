package dev.unnm3d.redischat.commands;

import dev.unnm3d.redischat.Permission;
import dev.unnm3d.redischat.RedisChat;
import dev.unnm3d.redischat.chat.ChatMessageInfo;
import dev.unnm3d.redischat.configs.Config;
import lombok.AllArgsConstructor;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public class MsgCommand implements CommandExecutor, TabCompleter {
    private final RedisChat plugin;

    public void sendMsg(String[] args, CommandSender sender, String receiverName) {

        String receiverPlaceholder = "%disguise_replace-name_" + receiverName + "%";


        if (!plugin.getPlayerListManager().getPlayerList().contains(receiverName)) {
            String finalReceiverName = receiverName;

            if(sender instanceof  Player player) {
                String tmp = PlaceholderAPI.setPlaceholders(player, receiverPlaceholder);
                if(!tmp.equals(receiverPlaceholder)) {
                    finalReceiverName = tmp;
                }
            }

            plugin.messages.sendMessage(sender, plugin.messages.player_not_online.replace("%player%", finalReceiverName));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            String message = String.join(" ", args);
            List<Config.ChatFormat> chatFormatList = plugin.config.getChatFormats(sender);
            if (chatFormatList.isEmpty()) return;

            String finalSenderName = sender.getName();
            String finalReceiverName = receiverName;

            String senderPlaceholder = "%disguise_replace-name_" + sender.getName() + "%";


            if(sender instanceof Player p) {
                String tmp = PlaceholderAPI.setPlaceholders(p, receiverPlaceholder);
                if(!tmp.equals(receiverPlaceholder)) {
                    finalReceiverName = tmp;
                }

                tmp = PlaceholderAPI.setPlaceholders(p, senderPlaceholder);
                if(!tmp.equals(senderPlaceholder)) {
                    finalSenderName = tmp;
                }
            }



            Component formatted = plugin.getComponentProvider().parse(sender, chatFormatList.get(0).private_format().replace("%receiver%", finalReceiverName).replace("%sender%", finalSenderName));

            //Check for minimessage tags permission
            boolean parsePlaceholders = true;
            if (!sender.hasPermission(Permission.REDIS_CHAT_USE_FORMATTING.getPermission())) {
                message = plugin.getComponentProvider().purgeTags(message);
                parsePlaceholders = false;
            }
            // remove blacklisted stuff
            message = plugin.getComponentProvider().sanitize(message);

            //Check inv update
            if (sender instanceof Player player) {
                if (message.contains("<inv>")) {
                    plugin.getRedisDataManager().addInventory(player.getName(), player.getInventory().getContents());
                }
                if (message.contains("<item>")) {
                    plugin.getRedisDataManager().addItem(player.getName(), player.getInventory().getItemInHand());
                }
                if (message.contains("<ec>")) {
                    plugin.getRedisDataManager().addEnderchest(player.getName(), player.getEnderChest().getContents());
                }
            }

            //Parse into minimessage (placeholders, tags and mentions)
            Component toBeReplaced = plugin.getComponentProvider().parse(sender, message, parsePlaceholders, true, true, plugin.getComponentProvider().getInvShareTagResolver(sender, chatFormatList.get(0)));
            //Put message into format
            formatted = formatted.replaceText(
                    builder -> builder.match("%message%").replacement(toBeReplaced)
            );
            //Send to other servers
            plugin.getRedisDataManager().sendObjectPacket(new ChatMessageInfo(sender.getName(), MiniMessage.miniMessage().serialize(toBeReplaced), receiverName));
            plugin.getChatListener().onSenderPrivateChat(sender, formatted);
            plugin.getRedisDataManager().setReplyName(receiverName, sender.getName());


        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permission.REDIS_CHAT_MESSAGE.getPermission())) return true;

        if (args.length < 2) {
            return true;
        }

        String receiverName = args[0];
        if (receiverName.equalsIgnoreCase(sender.getName())) {
            plugin.messages.sendMessage(sender, plugin.messages.cannot_message_yourself);
            return true;
        }
        // remove first arg[0], since it's the player name and we don't want to include it in the msg
        args = Arrays.copyOfRange(args, 1, args.length);
        // do some stuff to send the message
        sendMsg(args, sender, receiverName);

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0 || !sender.hasPermission(Permission.REDIS_CHAT_MESSAGE.getPermission())) return List.of();
        return plugin.getPlayerListManager().getPlayerList().stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
