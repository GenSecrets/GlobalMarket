package com.survivorserver.globalMarket;

import org.bukkit.ChatColor;

public class LocaleHandler {
	
    ConfigHandler config;
    String selected;

    public LocaleHandler(ConfigHandler config) {
        this.config = config;
        setSelected();
    }

    public void setSelected() {
        selected = config.getLocaleYML().getString("selected");
    }

    public String get(String string, Object...args) {
        if (!config.getLocaleYML().isSet(selected + "." + string)) {
            return string;
        }
        return ChatColor.translateAlternateColorCodes('&', String.format(config.getLocaleYML().getString(selected + "." + string), args));
    }

    public String get(String string) {
        if (!config.getLocaleYML().isSet(selected + "." + string)) {
            return string;
        }
        return ChatColor.translateAlternateColorCodes('&', config.getLocaleYML().getString(selected + "." + string));
    }

    public void registerLocale(String path, String value) {
        config.getLocaleYML().addDefault(selected + "." + path, value);
        config.getLocaleYML().options().copyDefaults(true);
    }
}
