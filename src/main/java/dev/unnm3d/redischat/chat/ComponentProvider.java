package dev.unnm3d.redischat.chat;

import dev.unnm3d.redischat.Permissions;
import dev.unnm3d.redischat.RedisChat;
import dev.unnm3d.redischat.api.TagResolverIntegration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
public class ComponentProvider {
    private final RedisChat plugin;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    @Getter
    private final TagResolver standardTagResolver;
    private final ConcurrentHashMap<Player, List<Component>> cacheBlocked;
    private final List<TagResolverIntegration> tagResolverIntegrationList;

    public ComponentProvider(RedisChat plugin) {
        this.plugin = plugin;
        this.audiences = BukkitAudiences.create(plugin);
        this.miniMessage = MiniMessage.miniMessage();
        this.cacheBlocked = new ConcurrentHashMap<>();
        this.standardTagResolver = StandardTags.defaults();
        this.tagResolverIntegrationList = new ArrayList<>();
    }

    /**
     * Add a custom tag resolver integration
     *
     * @param integration The integration to add
     */
    public void addResolverIntegration(TagResolverIntegration integration) {
        this.tagResolverIntegrationList.add(integration);
    }

    /**
     * Gets the custom placeholder resolver for a player
     *
     * @param commandSender The player
     * @return The custom placeholder resolver
     */
    public TagResolver getCustomPlaceholderResolver(CommandSender commandSender) {
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, String> placeholderEntry : plugin.config.placeholders.entrySet()) {
            builder.resolver(Placeholder.component(
                    placeholderEntry.getKey(),
                    parse(commandSender,
                            placeholderEntry.getValue(),
                            true,
                            false,
                            false,
                            this.standardTagResolver)));
        }
        return builder.build();
    }

    public Component parse(String text, TagResolver... tagResolvers) {
        return parse(null, text, tagResolvers);
    }

    public Component parse(String text) {
        return parse(text, this.standardTagResolver);
    }

    public Component parse(CommandSender player, String text, TagResolver... tagResolvers) {
        return parse(player, text, true, true, true, tagResolvers);
    }

    public @NotNull Component parse(@Nullable CommandSender player, @NotNull String text, boolean parsePlaceholders, boolean parseMentions, boolean parseLinks, @NotNull TagResolver... tagResolvers) {
        Map.Entry<String, Component> parsedLinks = new AbstractMap.SimpleEntry<>(null, null);
        if (parseLinks) {
            parsedLinks = parseLinks(text, plugin.config.formats.get(0));
            text = parsedLinks.getKey();
        }
        if (parseMentions) {
            text = parseMentions(text, plugin.config.formats.get(0));
        }
        if (plugin.config.legacyColorCodesSupport && (player == null || //Is without permissions or if it has permissions
                player.hasPermission(Permissions.USE_FORMATTING.getPermission()))) {
            text = parseLegacy(text);
        }

        Component finalComponent = parsePlaceholders ?
                parsePlaceholders(player, parseResolverIntegrations(text), tagResolvers) :
                miniMessage.deserialize(text, tagResolvers);

        if (parsedLinks.getValue() != null) {
            Map.Entry<String, Component> finalParsedLinks = parsedLinks;
            finalComponent = finalComponent.replaceText(rTextBuilder ->
                    rTextBuilder.matchLiteral("%link%")
                            .replacement(finalParsedLinks.getValue()));
        }
        return finalComponent;
    }

    public String replaceBukkitColorCodesWithSection(String text) {
        if (!plugin.config.legacyColorCodesSupport) { // if legacy color codes support is disabled, we don't need to replace anything
            return text;
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public Component parse(CommandSender player, String text) {
        return parse(player, text, this.standardTagResolver);
    }

    /**
     * Parse placeholders
     *
     * @param cmdSender    The command sender to parse the placeholders for
     * @param text         The text to parse
     * @param tagResolvers The tag resolvers to use
     * @return The parsed text
     */
    public @NotNull Component parsePlaceholders(CommandSender cmdSender, @NotNull String text, TagResolver... tagResolvers) {
        final String[] stringPlaceholders = text.split("%");
        final LinkedHashMap<String, Component> placeholders = new LinkedHashMap<>();
        int placeholderStep = 1;
        // we need to split the text by % and then check if the placeholder is a placeholder or not
        for (int i = 0; i < stringPlaceholders.length; i++) {
            if (i % 2 == placeholderStep) {
                final String reformattedPlaceholder = "%" + stringPlaceholders[i] + "%";
                final String parsedPlaceH = replaceBukkitColorCodesWithSection(
                        cmdSender instanceof OfflinePlayer offlinePlayer
                                ? PlaceholderAPI.setPlaceholders(offlinePlayer, reformattedPlaceholder)
                                : PlaceholderAPI.setPlaceholders(null, reformattedPlaceholder)
                );
                if (parsedPlaceH.equals(reformattedPlaceholder)) {
                    placeholderStep = Math.abs(placeholderStep - 1);
                    continue;
                }
                if (plugin.config.enablePlaceholderGlitch) {
                    text = text.replace(reformattedPlaceholder, miniMessage.serialize(LegacyComponentSerializer.legacySection().deserialize(parsedPlaceH)));
                } else if (parsedPlaceH.contains("§")) {
                    //Colored placeholder needs to be pasted after the normal text is parsed
                    placeholders.put(reformattedPlaceholder, LegacyComponentSerializer.legacySection().deserialize(parsedPlaceH));
                } else {
                    text = text.replace(reformattedPlaceholder, parsedPlaceH);
                }
            }
        }
        Component answer = miniMessage.deserialize(text, tagResolvers);
        for (String placeholder : placeholders.keySet()) {
            answer = answer.replaceText(rBuilder -> rBuilder.matchLiteral(placeholder).replacement(placeholders.get(placeholder)));
        }
        return answer;
    }

    /**
     * Purge MiniMessage tags from a text
     * Use this to prevent unprivileged players from using tags
     *
     * @param text The text to purge
     * @return The purged text
     */
    public @NotNull String purgeTags(@NotNull String text) {
        return miniMessage.stripTags(text, TagResolver.standard());
    }

    /**
     * Get the tag resolver for the invshare feature
     *
     * @param player The player to get the tag resolver for
     * @return The tag resolver
     */
    public @NotNull TagResolver getRedisChatTagResolver(@NotNull CommandSender player) {

        final TagResolver.Builder builder = TagResolver.builder();

        if (player.hasPermission(Permissions.USE_INVENTORY.getPermission())) {
            String toParseInv = plugin.config.inventoryFormat;
            toParseInv = toParseInv.replace("%player%", player.getName());
            toParseInv = toParseInv.replace("%command%", "/invshare " + player.getName() + "-inventory");
            TagResolver inv = Placeholder.component("inv", parse(player, toParseInv, true, false, false, this.standardTagResolver));
            builder.resolver(inv);
        }

        if (player.hasPermission(Permissions.USE_ITEM.getPermission())) {
            String toParseItem = plugin.config.itemFormat;
            toParseItem = toParseItem.replace("%player%", player.getName());
            toParseItem = toParseItem.replace("%command%", "/invshare " + player.getName() + "-item");
            Component toParseItemComponent = parse(player, toParseItem, true, false, false, this.standardTagResolver);
            if (player instanceof Player p) {
                if (!p.getInventory().getItemInMainHand().getType().isAir()) {
                    if (p.getInventory().getItemInMainHand().getItemMeta() != null)
                        if (p.getInventory().getItemInMainHand().getItemMeta().hasDisplayName()) {
                            toParseItemComponent = toParseItemComponent.replaceText(rTextBuilder ->
                                    rTextBuilder.matchLiteral("%item_name%")
                                            .replacement(
                                                    parse(player,
                                                            parseLegacy(p.getInventory().getItemInMainHand().getItemMeta().getDisplayName()),
                                                            false,
                                                            false,
                                                            false,
                                                            this.standardTagResolver))
                            );
                        } else {
                            toParseItemComponent = toParseItemComponent.replaceText(rTextBuilder ->
                                    rTextBuilder.matchLiteral("%item_name%")
                                            .replacement(
                                                    parse(player,
                                                            p.getInventory().getItemInMainHand().getType().name().toLowerCase().replace("_", " "),
                                                            false,
                                                            false,
                                                            false,
                                                            this.standardTagResolver))
                            );
                        }
                } else {
                    toParseItemComponent = toParseItemComponent.replaceText(rTextBuilder ->
                            rTextBuilder.matchLiteral("%item_name%")
                                    .replacement("Nothing")
                    );
                }
            }
            TagResolver item = Placeholder.component("item", toParseItemComponent);
            builder.resolver(item);
        }

        if (player.hasPermission(Permissions.USE_ENDERCHEST.getPermission())) {
            String toParseEnderChest = plugin.config.enderChestFormat;
            toParseEnderChest = toParseEnderChest.replace("%player%", player.getName());
            toParseEnderChest = toParseEnderChest.replace("%command%", "/invshare " + player.getName() + "-enderchest");
            TagResolver ec = Placeholder.component("ec", parse(player, toParseEnderChest, true, false, false, this.standardTagResolver));
            builder.resolver(ec);
        }
        if (player.hasPermission(Permissions.USE_CUSTOM_PLACEHOLDERS.getPermission()))
            builder.resolver(getCustomPlaceholderResolver(player));

        return builder.build();
    }

    /**
     * Parse mentions
     *
     * @param text   The text to parse
     * @param format The format to use
     * @return The parsed text
     */
    private String parseMentions(@NotNull String text, @NotNull ChatFormat format) {
        String toParse = text;
        for (String playerName : plugin.getPlayerListManager().getPlayerList()) {
            playerName = playerName.replace("*", "\\*");
            Pattern p = Pattern.compile("(^" + playerName + "|" + playerName + "$|\\s" + playerName + "\\s)"); //
            Matcher m = p.matcher(text);
            if (m.find()) {
                String replacing = m.group();
                replacing = replacing.replace(playerName, format.mention_format().replace("%player%", playerName));
                toParse = toParse.replace(m.group(), replacing);
                if (plugin.config.debug)
                    Bukkit.getLogger().info("mention parsed for " + playerName + " : " + toParse);
            }
        }

        return toParse;
    }

    /**
     * Parse links
     *
     * @param text   The text to parse
     * @param format The link format to use
     * @return The parsed text with %link% as a placeholder for the link component and the link component
     */
    private Map.Entry<String, Component> parseLinks(@NotNull String text, @NotNull ChatFormat format) {
        Component linkComponent = null;
        Pattern p = Pattern.compile("(https?://\\S+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String linkString = m.group();
            linkString = linkString.endsWith("/") ? // the last slash breaks the closing tag of <click>
                    linkString.substring(0, linkString.length() - 1) :
                    linkString;
            text = text.replace(m.group(), "%link%");//replace the link with a placeholder
            linkComponent = miniMessage.deserialize(format.link_format().replace("%link%", linkString));
        }

        if (plugin.config.debug)
            Bukkit.getLogger().info("links: " + text);
        return new AbstractMap.SimpleEntry<>(text, linkComponent);
    }

    /**
     * Parse text with the integration tag resolvers (Oraxen and other plugins)
     *
     * @param text The text to parse
     * @return The parsed text
     */
    private String parseResolverIntegrations(String text) {
        for (TagResolverIntegration resolver : this.tagResolverIntegrationList) {
            text = resolver.parseTags(text).replace("\\", "");
        }
        return text;
    }

    /**
     * Sanitize a message from blacklisted regexes
     *
     * @param message The message to sanitize
     * @return The sanitized message
     */
    public @NotNull String sanitize(@NotNull String message) {
        for (String regex : plugin.config.regex_blacklist) {
            message = message.replaceAll(regex, plugin.config.blacklistReplacement);
        }
        return message;
    }

    /**
     * Transform uppercase messages into lowercase
     *
     * @param message The message to transform
     * @return The transformed message
     */
    public boolean antiCaps(@NotNull String message) {
        int capsCount = 0;
        for (char c : message.toCharArray())
            if (Character.isUpperCase(c))
                capsCount++;
        return capsCount > message.length() / 2 && message.length() > 20;//50% of the message is caps and the message is longer than 20 chars
    }

    /**
     * Parse legacy color codes (§ and &)
     *
     * @param text The text to parse
     * @return The parsed text
     */
    public @NotNull String parseLegacy(@NotNull String text) {

        text = miniMessage.serialize(LegacyComponentSerializer.legacySection().deserialize(replaceBukkitColorCodesWithSection(text)));
        if (plugin.config.debug) {
            Bukkit.getLogger().info("Parsed legacy: " + text);
        }
        return text.replace("\\", "");

    }

    /**
     * Send a component to a player or caches it if the player has paused the chat
     *
     * @param player    The player to send the component to
     * @param component The component to send
     */
    public void sendComponentOrCache(@NotNull Player player, @NotNull Component component) {
        if (cacheBlocked.computeIfPresent(player,
                (player1, components) -> {
                    components.add(component);
                    return components;
                }) == null) {//If the player is not blocked
            plugin.getComponentProvider().sendMessage(player, component);
        }
    }

    public void logToConsole(Component component) {
        audiences.sender(plugin.getServer().getConsoleSender()).sendMessage(component);
    }

    public void pauseChat(@NotNull Player player) {
        cacheBlocked.put(player, new ArrayList<>());
    }


    public boolean isPaused(@NotNull Player player) {
        return cacheBlocked.containsKey(player);
    }

    /**
     * Unpause the chat for a player and send all cached components
     *
     * @param player The player to unpause the chat for
     */
    public void unpauseChat(@NotNull Player player) {
        if (cacheBlocked.containsKey(player)) {
            for (Component component : cacheBlocked.remove(player)) {
                plugin.getComponentProvider().sendMessage(player, component);
            }
        }
    }

    public void sendMessage(CommandSender p, String message) {
        audiences.sender(p).sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    public void sendMessage(CommandSender p, Component component) {
        audiences.sender(p).sendMessage(component);
    }

    public void openBook(Player player, Book book) {
        audiences.player(player).openBook(book);
    }
}

