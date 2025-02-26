package net.buycraft.plugin.platform;

import java.util.Locale;

public enum PlatformType {
    BUKKIT,
    NONE;

    public String platformName() {
        return name().toLowerCase(Locale.US);
    }
}
