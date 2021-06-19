package com.survivorserver.globalMarket;

import com.survivorserver.globalMarket.chat.ChatComponent;
import com.survivorserver.globalMarket.command.MarketCommand;
import com.survivorserver.globalMarket.legacy.Importer;
import com.survivorserver.globalMarket.lib.PacketManager;
import com.survivorserver.globalMarket.sql.AsyncDatabase;
import com.survivorserver.globalMarket.sql.Database;
import com.survivorserver.globalMarket.sql.StorageMethod;
import com.survivorserver.globalMarket.tasks.CleanTask;
import com.survivorserver.globalMarket.tasks.ExpireTask;
import com.survivorserver.globalMarket.tasks.Queue;
import com.survivorserver.globalMarket.ui.IHandler;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

@SuppressWarnings({"unused", "deprecation", "TypeMayBeWeakened"})
public class Market extends JavaPlugin implements Listener {
    
    static Market market;
    public Logger log;
    public String infiniteSeller;
    String prefix;
    private List<Integer> tasks;
    private ConfigHandler config;
    private InterfaceHandler interfaceHandler;
    private MarketCore core;
    @SuppressWarnings("FieldCanBeLocal")
    private InterfaceListener listener;
    private Economy econ;
    private Permission perms;
    private LocaleHandler locale;
    private Map<String, String> searching;
    private MarketCommand cmd;
    private TabCompletion tab;
    private HistoryHandler history;
    private AsyncDatabase asyncDb;
    private MarketStorage storage;
    private Map<String, String[]> worldLinks;
    private PacketManager packet;
    //private ItemIndex items;
    private ChatComponent chat;
    private boolean mcpcp;
    
    public static Market getMarket() {
        return market;
    }
    
    public void onEnable() {
        log = getLogger();
        tasks = new ArrayList<>();
        market = this;
        reloadConfig();
        getConfig().options().header("GlobalMarket config: " + getDescription().getVersion());
        getConfig().addDefault("storage.type", StorageMethod.SQLITE.toString());
        getConfig().addDefault("storage.mysql_user", "root");
        getConfig().addDefault("storage.mysql_pass", "password");
        getConfig().addDefault("storage.mysql_database", "market");
        getConfig().addDefault("storage.mysql_address", "localhost");
        getConfig().addDefault("storage.mysql_port", 3306);
        getConfig().addDefault("multiworld.enable", false);
        getConfig().addDefault("multiworld.links", Collections.emptyList());
        getConfig().addDefault("limits.default.cut", 0.05);
        getConfig().addDefault("limits.default.max_price", 0.0);
        getConfig().addDefault("limits.default.max_item_prices.air.dmg", 0);
        getConfig().addDefault("limits.default.max_item_prices.air.price", 50.0);
        getConfig().addDefault("limits.default.creation_fee", 0.05);
        getConfig().addDefault("limits.default.max_listings", 0);
        getConfig().addDefault("limits.default.expire_time", 0);
        getConfig().addDefault("limits.default.queue_trade_time", 0);
        getConfig().addDefault("limits.default.queue_mail_time", 0);
        getConfig().addDefault("limits.default.allow_creative", true);
        getConfig().addDefault("limits.default.max_mail", 0);
        getConfig().addDefault("queue.queue_mail_on_buy", true);
        getConfig().addDefault("queue.queue_on_cancel", true);
        getConfig().addDefault("infinite.seller", "Server");
        getConfig().addDefault("infinite.account", "");
        getConfig().addDefault("blacklist.as_whitelist", false);
        getConfig().addDefault("blacklist.custom_names", false);
        getConfig().addDefault("blacklist.item_name", Arrays.asList("Transaction Log", "Market History"));
        getConfig().addDefault("blacklist.item_id.0", 0);
        getConfig().addDefault("blacklist.enchant_id", Collections.emptyList());
        getConfig().addDefault("blacklist.lore", Collections.emptyList());
        getConfig().addDefault("blacklist.use_with_mail", false);
        getConfig().addDefault("automatic_payments", false);
        getConfig().addDefault("enable_history", true);
        getConfig().addDefault("announce_new_listings", true);
        getConfig().addDefault("stall_radius", 0);
        getConfig().addDefault("mailbox_radius", 0);
        getConfig().addDefault("new_mail_notification", true);
        getConfig().addDefault("new_mail_notification_delay", 10);
        getConfig().addDefault("enable_metrics", true);
        getConfig().addDefault("notify_on_update", true);
        
        getConfig().options().copyDefaults(true);
        saveConfig();
        
        final File langFile = new File(getDataFolder().getAbsolutePath() + File.separator + "en_US.lang");
        if(!langFile.exists()) {
            saveResource("en_US.lang", true);
        }
        //items = new ItemIndex(this);
        
        final RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if(economyProvider != null) {
            econ = economyProvider.getProvider();
        } else {
            log.severe("Vault has no hooked economy plugin, disabling");
            setEnabled(false);
            return;
        }
        final RegisteredServiceProvider<Permission> permsProvider = getServer().getServicesManager().getRegistration(Permission.class);
        if(permsProvider != null) {
            perms = permsProvider.getProvider();
        } else {
            log.warning("You do not have a Vault-enabled permissions plugin. Defaulting to default player limits under limits.default in config.yml.");
        }
        boolean plib = false;
        try {
            Class.forName("com.comphenix.protocol.ProtocolManager");
            plib = true;
        } catch(final Exception ignored) {
        }
        if(plib) {
            if(Material.getMaterial("LOG_2") != null) {
                packet = new PacketManager(this);
            } else {
                log.info("ProtocolLib was found but GM only supports ProtocolLib for 1.7 and above.");
            }
        }
        try {
            Class.forName("me.dasfaust.globalMarket.MarketCompanion");
            log.info("Market Forge mod detected!");
            mcpcp = true;
        } catch(final Exception ignored) {
        }
        config = new ConfigHandler(this);
        locale = new LocaleHandler(config);
        prefix = locale.get("cmd.prefix");
        cmd = new MarketCommand(this);
        tab = new TabCompletion();
        getCommand("market").setExecutor(cmd);
        getCommand("market").setTabCompleter(tab);
        asyncDb = new AsyncDatabase(this);
        storage = new MarketStorage(this, asyncDb);
        worldLinks = new HashMap<>();
        initializeStorage();
    }
    
    public void initializeStorage() {
        final Database db = config.createConnection();
        if(!db.connect()) {
            log.severe("Couldn't connect to the configured database! GlobalMarket can't continue without a connection, please check your config and do /market reload or restart your server");
            return;
        }
        storage.loadSchema(db);
        storage.load(db);
        db.close();
        asyncDb.startTask();
        if(interfaceHandler == null) {
            intialize();
        }
    }
    
    public void intialize() {
        if(Importer.importNeeded(this)) {
            Importer.importLegacyData(config, storage, this);
        }
        interfaceHandler = new InterfaceHandler(this, storage);
        interfaceHandler.registerInterface(new ListingsInterface(this));
        interfaceHandler.registerInterface(new MailInterface(this));
        core = new MarketCore(this, interfaceHandler, storage);
        listener = new InterfaceListener(this, interfaceHandler, storage, core);
        getServer().getPluginManager().registerEvents(listener, this);
        tasks.add(new ExpireTask(this, config, core, storage).runTaskTimerAsynchronously(this, 0, 72000).getTaskId());
        tasks.add(getServer().getScheduler().scheduleSyncRepeatingTask(this, new CleanTask(this, interfaceHandler), 0, 20));
        tasks.add(new Queue(this).runTaskTimer(this, 0, 1200).getTaskId());
        if(getConfig().getBoolean("enable_metrics")) {
            try {
                final MetricsLite metrics = new MetricsLite(this);
                metrics.start();
            } catch(final Exception e) {
                log.info("Failed to start Metrics!");
            }
        }
        searching = new HashMap<>();
        if(enableHistory()) {
            history = new HistoryHandler(this, asyncDb, config);
        }
        infiniteSeller = getConfig().getString("infinite.seller");
        getServer().getPluginManager().registerEvents(this, this);
        if(enableMultiworld()) {
            buildWorldLinks();
        }
        chat = new ChatComponent(this);
    }
    
    //public ItemIndex getItemIndex() {
    //    return items;
    //}
    
    public ChatComponent getChat() {
        return chat;
    }
    
    public Economy getEcon() {
        return econ;
    }
    
    public Permission getPerms() {
        return perms;
    }
    
    public MarketStorage getStorage() {
        return storage;
    }
    
    public MarketCore getCore() {
        return core;
    }
    
    public LocaleHandler getLocale() {
        return locale;
    }
    
    public ConfigHandler getConfigHandler() {
        return config;
    }
    
    public InterfaceHandler getInterfaceHandler() {
        return interfaceHandler;
    }
    
    public boolean mcpcpSupportEnabled() {
        return mcpcp;
    }
    
    public boolean useProtocolLib() {
        return packet != null;
    }
    
    public PacketManager getPacket() {
        return packet;
    }
    
    public int getStallRadius() {
        return getConfig().getInt("stall_radius");
    }
    
    public int getMailboxRadius() {
        return getConfig().getInt("mailbox_radius");
    }
    
    public boolean announceOnCreate() {
        return getConfig().getBoolean("announce_new_listings");
    }
    
    public int getMaxMail(final String player, final String world) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.has(world, player, "globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".max_mail");
            }
        }
        return getConfig().getInt("limits.default.max_mail");
    }
    
    @SuppressWarnings("TypeMayBeWeakened")
    public int getMaxMail(final Player player) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".max_mail");
            }
        }
        return getConfig().getInt("limits.default.max_mail");
    }
    
    public double getCut(final double amount, final String playerName, final String world) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            OfflinePlayer player = getServer().getOfflinePlayer(playerName);
            final boolean[] hasPerms = {false};
            Thread offlineTemp = new Thread(){
                public void run(){
                    if(perms.playerHas(world, player, "globalmarket.limits." + k)){
                        hasPerms[0] = true;
                    }
                }
            };
            offlineTemp.start();

            if(hasPerms[0]) {
                if(getConfig().isDouble("limits." + k + ".cut")) {
                    return new BigDecimal(amount * getConfig().getDouble("limits." + k + ".cut")).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                } else {
                    return getConfig().getDouble("limits." + k + ".cut");
                }
            }
        }
        if(getConfig().isDouble("limits.default.cut")) {
            return new BigDecimal(amount * getConfig().getDouble("limits.default.cut")).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
        } else {
            return getConfig().getDouble("limits.default.cut");
        }
    }
    
    public double getCreationFee(final Player player, final double price) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                if(getConfig().isDouble("limits." + k + ".creation_fee")) {
                    return new BigDecimal(price * getConfig().getDouble("limits." + k + ".creation_fee")).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                } else {
                    return getConfig().getDouble("limits." + k + ".creation_fee");
                }
            }
        }
        if(getConfig().isDouble("limits.default.creation_fee")) {
            return new BigDecimal(price * getConfig().getDouble("limits.default.creation_fee")).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
        } else {
            return getConfig().getDouble("limits.default.creation_fee");
        }
    }
    
    public boolean autoPayment() {
        return getConfig().getBoolean("automatic_payments");
    }
    
    public void addSearcher(final String name, final String interfaceName) {
        searching.put(name, interfaceName);
    }
    
    @SuppressWarnings("deprecation")
    public double getMaxPrice(final Player player, final ItemStack item) {
        String limitGroup = "default";
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                limitGroup = k;
            }
        }
        String itemPath = "limits." + limitGroup + ".max_item_prices." + item.getType().toString().toLowerCase();
        boolean hasPrice = false;
        if(getConfig().isSet(itemPath)) {
            hasPrice = true;
        } else {
            itemPath = "limits." + limitGroup + ".max_item_prices." + item.getType();
            if(getConfig().isSet(itemPath)) {
                hasPrice = true;
            }
        }
        if(hasPrice) {
            final int dmg = getConfig().getInt(itemPath + ".dmg");
            if(dmg == -1 || dmg == item.getDurability()) {
                return getConfig().getDouble(itemPath + ".price");
            }
        }
        return getConfig().getDouble("limits." + limitGroup + ".max_price");
    }
    
    public double getMaxPrice(final String player, final String world, final ItemStack item) {
        String limitGroup = "default";
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.playerHas(world, player, "globalmarket.limits." + k)) {
                limitGroup = k;
            }
        }
        final String itemPath = "limits." + limitGroup + ".max_item_prices." + item.getType().toString().toLowerCase();
        if(getConfig().isSet(itemPath)) {
            final int dmg = getConfig().getInt(itemPath + ".dmg");
            if(dmg == -1 || dmg == item.getDurability()) {
                return getConfig().getDouble(itemPath + ".price");
            }
        }
        return getConfig().getDouble("limits." + limitGroup + ".max_price");
    }
    
    public void startSearch(final Player player, final String interfaceName) {
        player.sendMessage(ChatColor.GREEN + getLocale().get("type_your_search"));
        final String name = player.getName();
        if(!searching.containsKey(name)) {
            addSearcher(name, interfaceName);
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if(searching.containsKey(name)) {
                    searching.remove(name);
                    final Player player1 = getServer().getPlayer(name);
                    if(player1 != null) {
                        player1.sendMessage(prefix + getLocale().get("search_cancelled"));
                    }
                }
            }, 200);
        }
    }
    
    public int getTradeTime(final Player player) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".queue_trade_time");
            }
        }
        return getConfig().getInt("limits.default.queue_trade_time");
    }
    
    public int getTradeTime(final String player, final String world) {
        if(perms == null) {
            return getConfig().getInt("limits.default.queue_trade_time");
        }
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.playerHas(world, player, "globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".queue_trade_time");
            }
        }
        return getConfig().getInt("limits.default.queue_trade_time");
    }
    
    public int getMailTime(final Player player) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".queue_mail_time");
            }
        }
        return getConfig().getInt("limits.default.queue_mail_time");
    }
    
    public int getMailTime(final String player, final String world) {
        if(perms == null) {
            return getConfig().getInt("limits.default.queue_mail_time");
        }
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.playerHas(world, player, "globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".queue_mail_time");
            }
        }
        return getConfig().getInt("limits.default.queue_mail_time");
    }
    
    public boolean queueOnBuy() {
        return getConfig().getBoolean("queue.queue_mail_on_buy");
    }
    
    public boolean queueOnCancel() {
        return getConfig().getBoolean("queue.queue_mail_on_cancel");
    }
    
    public int maxListings(final Player player) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".max_listings");
            }
        }
        return getConfig().getInt("limits.default.max_listings");
    }
    
    public int maxListings(final String player, final String world) {
        if(perms == null) {
            return getConfig().getInt("limits.default.max_listings");
        }
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.playerHas(world, player, "globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".max_listings");
            }
        }
        return getConfig().getInt("limits.default.max_listings");
    }
    
    public int getExpireTime(final String player, final String world) {
        if(perms == null) {
            return getConfig().getInt("limits.default.expire_time");
        }
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(perms.playerHas(world, player, "globalmarket.limits." + k)) {
                return getConfig().getInt("limits." + k + ".expire_time");
            }
        }
        return getConfig().getInt("limits.default.expire_time");
    }
    
    @SuppressWarnings("deprecation")
    public boolean itemBlacklisted(final ItemStack item) {
        final boolean isWhitelist = getConfig().getBoolean("blacklist.as_whitelist");
        if(getConfig().isSet("blacklist.item_id." + item.getType())) {
            final String path = "blacklist.item_id." + item.getType();
            if(getConfig().isList(path)) {
                if(getConfig().getIntegerList(path).contains((int) item.getDurability())) {
                    return !isWhitelist;
                }
            } else {
                if(getConfig().getInt(path) == -1 || getConfig().getInt(path) == item.getDurability()) {
                    return !isWhitelist;
                }
            }
        }
        if(item.hasItemMeta()) {
            final ItemMeta meta = item.getItemMeta();
            final List<String> bl = getConfig().getStringList("blacklist.item_name");
            if(meta.hasDisplayName()) {
                if(getConfig().getBoolean("blacklist.custom_names")) {
                    return !isWhitelist;
                }
                for(final String str : bl) {
                    if(meta.getDisplayName().equalsIgnoreCase(str)) {
                        return !isWhitelist;
                    }
                }
            }
            if(meta instanceof BookMeta) {
                if(((BookMeta) meta).hasTitle()) {
                    for(final String str : bl) {
                        if(((BookMeta) meta).getTitle().equalsIgnoreCase(str)) {
                            return !isWhitelist;
                        }
                    }
                }
            }
            if(meta.hasEnchants()) {
                final List<String> ebl = getConfig().getStringList("blacklist.enchant_id");
                for(final Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    if(ebl.contains(entry.getKey().toString())) {
                        return !isWhitelist;
                    }
                }
            }
            if(meta.hasLore()) {
                final List<String> lbl = getConfig().getStringList("blacklist.lore");
                final List<String> lore = meta.getLore();
                if(getConfig().getBoolean("blacklist.use_partial_lore")){
                    for(final String str : lbl) {
                        for(final String line : lore) {
                            if(line.contains(str)) {
                                return !isWhitelist;
                            }
                        }
                    }
                }else {
                    for(final String str : lbl) {
                        if(lore.contains(str)) {
                            return !isWhitelist;
                        }
                    }
                }
            }
        }
        return isWhitelist;
    }
    
    public boolean blacklistMail() {
        return getConfig().getBoolean("blacklist.use_with_mail");
    }
    
    public boolean enableHistory() {
        return getConfig().getBoolean("enable_history");
    }
    
    public String getInfiniteSeller() {
        return infiniteSeller;
    }
    
    public String getInfiniteAccount() {
        return getConfig().getString("infinite.account");
    }
    
    public String getItemName(final ItemStack item) {
        //if(mcpcp && item instanceof WrappedItemStack) {
        //    return ((WrappedItemStack) item).getItemName();
        //}
        final String itemName = item.getType().name();
        if(item.getAmount() > 1) {
            return locale.get("friendly_item_name_with_amount", item.getAmount(), itemName);
        } else {
            return locale.get("friendly_item_name", itemName);
        }
    }
    
    public String getItemNameSingle(final ItemStack item) {
        return item.getType().name();
    }
    
    public MarketCommand getCmd() {
        return cmd;
    }
    
    public HistoryHandler getHistory() {
        return history;
    }
    
    public boolean enableMultiworld() {
        return getConfig().getBoolean("multiworld.enable");
    }
    
    public String[] getLinkedWorlds(final String world) {
        return worldLinks.containsKey(world) ? worldLinks.get(world) : new String[0];
    }
    
    public void buildWorldLinks() {
        worldLinks.clear();
        final Map<String, List<String>> links = new HashMap<>();
        final ConfigurationSection section = getConfig().getConfigurationSection("multiworld.links");
        if(section != null) {
            final Set<String> linkList = section.getKeys(false);
            for(final World wor : getServer().getWorlds()) {
                final String world = wor.getName();
                links.put(world, linkList.contains(world) ? getConfig().getStringList("multiworld.links." + world) : new ArrayList<>());
                linkList.stream().filter(w -> !w.equalsIgnoreCase(world))
                        .filter(w -> getConfig().getStringList("multiworld.links." + w).contains(world))
                        .filter(w -> !links.get(world).contains(w)).forEach(w -> links.get(world).add(w));
            }
            for(final Entry<String, List<String>> entry : links.entrySet()) {
                worldLinks.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
        }
    }
    
    public boolean allowCreative(final Player player) {
        for(final String k : getConfig().getConfigurationSection("limits").getKeys(false)) {
            if(player.hasPermission("globalmarket.limits." + k)) {
                return getConfig().getBoolean("limits." + k + ".allow_creative");
            }
        }
        return getConfig().getBoolean("limits.default.allow_creative");
    }
    
    public void notifyPlayer(final String who, final String notification) {
        final Player player = getServer().getPlayer(who);
        if(player != null) {
            player.sendMessage(locale.get("cmd.prefix") + notification);
        }
        for(final IHandler handler : interfaceHandler.getHandlers()) {
            handler.notifyPlayer(who, notification);
        }
    }
    
    public List<Location> getStallLocations() {
        final List<Location> locations = new ArrayList<>();
        if(getConfig().isSet("stall")) {
            for(final String loc : getConfig().getConfigurationSection("stall").getKeys(false)) {
                try {
                    locations.add(locationFromString(loc));
                } catch(final IllegalArgumentException ignored) {
                }
            }
        }
        return locations;
    }
    
    public List<Location> getMailboxLocations() {
        final List<Location> locations = new ArrayList<>();
        if(getConfig().isSet("mailbox")) {
            for(final String loc : getConfig().getConfigurationSection("mailbox").getKeys(false)) {
                try {
                    locations.add(locationFromString(loc));
                } catch(final IllegalArgumentException ignored) {
                }
            }
        }
        return locations;
    }
    
    public String locationToString(final Location loc) {
        return loc.getWorld().getName() +
                ',' +
                loc.getBlockX() +
                ',' +
                loc.getBlockY() +
                ',' +
                loc.getBlockZ();
    }
    
    public Location locationFromString(final String loc) {
        final String[] xyz = loc.split(",");
        if(xyz.length < 4) {
            throw new IllegalArgumentException("Invalid location string");
        }
        final World world = Bukkit.getServer().getWorld(xyz[0]);
        if(world == null) {
            throw new IllegalArgumentException("World no longer exists");
        }
        return new Location(world, Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]), Double.parseDouble(xyz[3]));
    }
    
    public void onQuit(final PlayerQuitEvent event) {
        if(interfaceHandler != null) {
            interfaceHandler.purgeViewer(event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if(searching.containsKey(player.getName())) {
            event.setCancelled(true);
            final String search = event.getMessage();
            if(search.equalsIgnoreCase("cancel")) {
                interfaceHandler.openInterface(player, null, searching.get(player.getName()));
                searching.remove(player.getName());
            } else {
                interfaceHandler.openInterface(player, search, searching.get(player.getName()));
                searching.remove(player.getName());
            }
        }
    }
    
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(final PlayerInteractEvent event) {
        if(!event.isCancelled() && event.getClickedBlock() != null) {
            if(isChestOrSign(event.getClickedBlock().getType())) {
                final Player player = event.getPlayer();
                final Location location = event.getClickedBlock().getLocation();
                final String loc = locationToString(location);
                if(getConfig().isSet("mailbox." + loc)) {
                    if(player.getGameMode() == GameMode.CREATIVE && !allowCreative(player)) {
                        player.sendMessage(ChatColor.RED + locale.get("not_allowed_while_in_creative"));
                        return;
                    }
                    event.setCancelled(true);
                    interfaceHandler.openInterface(player, null, "Mail");
                }
                if(getConfig().isSet("stall." + loc)) {
                    if(player.getGameMode() == GameMode.CREATIVE && !allowCreative(player)) {
                        player.sendMessage(ChatColor.RED + locale.get("not_allowed_while_in_creative"));
                        return;
                    }
                    event.setCancelled(true);
                    if(isSign(event.getClickedBlock().getType())) {
                        final Sign sign = (Sign) event.getClickedBlock().getState();
                        final String line = sign.getLine(3);
                        if(line != null && !line.isEmpty()) {
                            interfaceHandler.openInterface(player, line, "Listings");
                            return;
                        }
                    }
                    if(event.getPlayer().isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        startSearch(player, "Listings");
                    } else {
                        interfaceHandler.openInterface(player, null, "Listings");
                    }
                }
            }
        }
        final ItemStack item = event.getPlayer().getItemInHand();
        if(item != null && InterfaceListener.isMarketItem(item)) {
            item.setType(Material.AIR);
        }
    }
    
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onBlockBreak(final BlockBreakEvent event) {
        if(event.isCancelled()) {
            return;
        }
        final Block block = event.getBlock();
        if(isChestOrSign(block.getType())) {
            final Location location = block.getLocation();
            final String loc = locationToString(location);
            if(getConfig().isSet("mailbox." + loc)) {
                getConfig().set("mailbox." + loc, null);
                saveConfig();
                event.getPlayer().sendMessage(ChatColor.YELLOW + locale.get("mailbox_removed"));
            }
            if(getConfig().isSet("stall." + loc)) {
                getConfig().set("stall." + loc, null);
                saveConfig();
                event.getPlayer().sendMessage(ChatColor.YELLOW + locale.get("stall_removed"));
            }
        }
    }
    
    @EventHandler
    public void onLogin(final PlayerJoinEvent event) {
        final String name = event.getPlayer().getName();
        if(getConfig().getBoolean("new_mail_notification")) {
            new BukkitRunnable() {
                public void run() {
                    final Player player = market.getServer().getPlayer(name);
                    if(player != null) {
                        if(storage.getNumMail(player.getName(), player.getWorld().getName(), false) > 0) {
                            player.sendMessage(prefix + locale.get("you_have_new_mail"));
                        }
                    }
                }
            }.runTaskLater(this, getConfig().getInt("new_mail_notification_delay"));
        }
    }
    
    public void onDisable() {
        interfaceHandler.closeAllInterfaces();
        for(final Integer task : tasks) {
            getServer().getScheduler().cancelTask(task);
        }
        asyncDb.cancel();
        if(asyncDb.isProcessing()) {
            log.info("Please wait while the database queue is processed.");
            //noinspection StatementWithEmptyBody
            while(asyncDb.isProcessing()) {
            }
        }
        asyncDb.processQueue(true);
        asyncDb.close();
        if(packet != null) {
            packet.unregister();
        }
    }

    public boolean isChestOrSign(Material mat){
        return mat == Material.CHEST
                || mat == Material.TRAPPED_CHEST
                || isSign(mat);

    }

    public boolean isSign(Material mat){
        return mat == Material.ACACIA_SIGN
                || mat == Material.ACACIA_WALL_SIGN
                || mat == Material.BIRCH_SIGN
                || mat == Material.BIRCH_WALL_SIGN
                || mat == Material.CRIMSON_SIGN
                || mat == Material.CRIMSON_WALL_SIGN
                || mat == Material.DARK_OAK_SIGN
                || mat == Material.DARK_OAK_WALL_SIGN
                || mat == Material.JUNGLE_SIGN
                || mat == Material.JUNGLE_WALL_SIGN
                || mat == Material.OAK_SIGN
                || mat == Material.OAK_WALL_SIGN
                || mat == Material.SPRUCE_SIGN
                || mat == Material.SPRUCE_WALL_SIGN
                || mat == Material.WARPED_SIGN
                || mat == Material.WARPED_WALL_SIGN;
    }
}