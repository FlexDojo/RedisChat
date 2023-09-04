package dev.unnm3d.redischat;

public enum Permissions {
    MESSAGE("redischat.message"),
    MAIL_WRITE("redischat.mail.write"),
    MAIL_WRITE_PUBLIC("redischat.mail.writepublic"),
    MAIL_READ("redischat.mail.read"),
    IGNORE("redischat.ignore"),
    USE_FORMATTING("redischat.useformatting"),
    USE_ITEM("redischat.showitem"),
    USE_LEGACY_FORMATTNG_ITEM("redischat.showlegacyitem"),
    USE_INVENTORY("redischat.showinv"),
    USE_ENDERCHEST("redischat.showenderchest"),
    USE_CUSTOM_PLACEHOLDERS("redischat.usecustomplaceholders"),
    BROADCAST("redischat.broadcast"),
    CLEARCHAT("redischat.clearchat"),
    ADMIN("redischat.admin"),
    ADMIN_EDIT("redischat.editmessage"),
    ADMIN_STAFF_CHAT("redischat.staffchat"),
    BYPASS_RATE_LIMIT("redischat.bypass_rate_limit"),
    CHANNEL_PREFIX("redischat.channel."),
    CHANNEL_PUBLIC("redischat.channel.public"),
    CHANNEL_CREATE("redischat.createchannel"),
    CHANNEL_DELETE("redischat.deletechannel"),
    CHANNEL_TOGGLE_PLAYER("redischat.playerchannel"),
    ;

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}