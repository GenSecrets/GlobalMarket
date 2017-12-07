package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.sql.Database;
import com.survivorserver.globalMarket.sql.StorageMethod;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigHandler {
    
    private final Market market;
    private FileConfiguration localeConfig;
    private File localeFile;
    private final Map<String, YamlConfiguration> playerConfigs;
    
    public ConfigHandler(final Market market) {
        this.market = market;
        playerConfigs = new HashMap<>();
    }
    
    public Database createConnection() {
        if(getStorageMethod() == StorageMethod.MYSQL) {
            return new Database(market.getLogger(),
                    market.getConfig().getString("storage.mysql_user"),
                    market.getConfig().getString("storage.mysql_pass"),
                    market.getConfig().getString("storage.mysql_address"),
                    market.getConfig().getString("storage.mysql_database"),
                    market.getConfig().getInt("storage.mysql_port"));
        } else {
            return new Database(market.getLogger(), "", "", "", "data", market.getDataFolder().getAbsolutePath());
        }
    }
    
    public Database createConnection(final StorageMethod type) {
        if(type == StorageMethod.MYSQL) {
            return new Database(market.getLogger(),
                    market.getConfig().getString("storage.mysql_user"),
                    market.getConfig().getString("storage.mysql_pass"),
                    market.getConfig().getString("storage.mysql_address"),
                    market.getConfig().getString("storage.mysql_database"),
                    market.getConfig().getInt("storage.mysql_port"));
        } else {
            return new Database(market.getLogger(), "", "", "", "data", market.getDataFolder().getAbsolutePath());
        }
    }
    
    public StorageMethod getStorageMethod() {
        return StorageMethod.valueOf(market.getConfig().getString("storage.type").toUpperCase());
    }
    
    public void reloadLocaleYML() {
        localeFile = new File(market.getDataFolder(), "locale.yml");
        localeConfig = YamlConfiguration.loadConfiguration(localeFile);
        final InputStream defaults = market.getResource("locale.yml");
        final YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults));
        localeConfig.addDefaults(def);
        localeConfig.options().copyDefaults(true);
        localeConfig.set("version", def.get("version"));
        saveLocaleYML();
    }
    
    public FileConfiguration getLocaleYML() {
        if(localeConfig == null) {
            reloadLocaleYML();
        }
        return localeConfig;
    }
    
    public void saveLocaleYML() {
        if(localeConfig == null) {
            return;
        }
        try {
            getLocaleYML().save(localeFile);
        } catch(final Exception e) {
            market.getLogger().log(Level.SEVERE, "Could not save locale: ", e);
        }
    }
    
    public YamlConfiguration getPlayerConfig(final String player) {
        if(playerConfigs.containsKey(player)) {
            return playerConfigs.get(player);
        }
        final File playerFolder = new File(market.getDataFolder().getAbsolutePath() + File.separator + "players");
        if(!playerFolder.exists()) {
            try {
                playerFolder.mkdirs();
            } catch(final Exception e) {
                e.printStackTrace();
            }
        }
        final File playerFile = new File(market.getDataFolder().getAbsolutePath() + File.separator + "players" + File.separator + player + ".yml");
        final YamlConfiguration conf = YamlConfiguration.loadConfiguration(playerFile);
        playerConfigs.put(player, conf);
        return conf;
    }
    
    public void savePlayerConfig(final String player) {
        final File playerFile = new File(market.getDataFolder().getAbsolutePath() + File.separator + "players" + File.separator + player + ".yml");
        try {
            getPlayerConfig(player).save(playerFile);
        } catch(final Exception e) {
            e.printStackTrace();
        }
    }
}
