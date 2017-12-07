package com.survivorserver.globalMarket;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.survivorserver.globalMarket.lib.SearchResult;
import com.survivorserver.globalMarket.lib.SortMethod;
import com.survivorserver.globalMarket.lib.cauldron.CauldronHelper;
import com.survivorserver.globalMarket.sql.*;
import com.survivorserver.globalMarket.ui.IMarketItem;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings("unused")
public class MarketStorage {

    private final Market market;
    private final AsyncDatabase asyncDb;
    private final Map<Integer, ItemStack> items;
    private final Map<Integer, Listing> listings;
    private final Map<String, TreeSet<Listing>> worldListings;
    private final Map<Integer, Mail> mail;
    private final Map<String, List<Mail>> worldMail;
    private final Map<Integer, QueueItem> queue;
    private final TreeSet<Listing> condensedListings;
    private int itemIndex = 1;
    private int listingIndex = 1;
    private int mailIndex = 1;
    private int queueIndex = 1;

    public MarketStorage(final Market market, final AsyncDatabase asyncDb) {
        this.market = market;
        this.asyncDb = asyncDb;
        items = new HashMap<>();
        listings = new LinkedHashMap<>();
        worldListings = new HashMap<>();
        mail = new LinkedHashMap<>();
        worldMail = new HashMap<>();
        queue = new LinkedHashMap<>();
        condensedListings = new TreeSet<>();
    }

    public void loadSchema(final Database db) {
        final boolean sqlite = market.getConfigHandler().getStorageMethod() == StorageMethod.SQLITE;
        try {
            // Create items table
            db.createStatement("CREATE TABLE IF NOT EXISTS items ("
                    + (sqlite ? "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " : "id int NOT NULL PRIMARY KEY AUTO_INCREMENT, ")
                    + (sqlite ? "item MEDIUMTEXT UNIQUE" : "item MEDIUMTEXT CHARACTER SET utf8 COLLATE utf8_general_ci")
                    + ')').execute();
            // Create listings table
            db.createStatement("CREATE TABLE IF NOT EXISTS listings ("
                    + (sqlite ? "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " : "id int NOT NULL PRIMARY KEY AUTO_INCREMENT, ")
                    + "seller TINYTEXT, "
                    + "item int, "
                    + "amount int, "
                    + "price DOUBLE, "
                    + "world TINYTEXT, "
                    + "time BIGINT)").execute();
            // Create mail table
            db.createStatement("CREATE TABLE IF NOT EXISTS mail ("
                    + (sqlite ? "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " : "id int NOT NULL PRIMARY KEY AUTO_INCREMENT, ")
                    + "owner TINYTEXT, "
                    + "item int, "
                    + "amount int, "
                    + "sender TINYTEXT, "
                    + "world TINYTEXT, "
                    + "pickup DOUBLE)").execute();
            // Create queue table
            db.createStatement("CREATE TABLE IF NOT EXISTS queue ("
                    + (sqlite ? "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " : "id int NOT NULL PRIMARY KEY AUTO_INCREMENT, ")
                    + "data MEDIUMTEXT)").execute();
            // Create users metadata table
            db.createStatement("CREATE TABLE IF NOT EXISTS users ("
                    + "name varchar(16) NOT NULL UNIQUE, "
                    + "earned DOUBLE, "
                    + "spent DOUBLE)").execute();
            // Create history table
            db.createStatement("CREATE TABLE IF NOT EXISTS history ("
                    + (sqlite ? "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " : "id int NOT NULL PRIMARY KEY AUTO_INCREMENT, ")
                    + "player TINYTEXT, "
                    + "action TINYTEXT, "
                    + "who TINYTEXT, "
                    + "item int, "
                    + "amount int, "
                    + "price DOUBLE, "
                    + "time BIGINT)").execute();
            if (sqlite) {
                db.createStatement("pragma journal_mode=wal").query();
            }
        } catch(final Exception e) {
            market.log.severe("Error while preparing database:");
            e.printStackTrace();
        }
    }

    public void load(final Database db) {
        final String dbName = market.getConfig().getString("storage.mysql_database");
        final boolean sqlite = market.getConfigHandler().getStorageMethod() == StorageMethod.SQLITE;
        try {
            loadItems(db, dbName, sqlite);
            loadListings(db, dbName, sqlite);
            loadMail(db, dbName, sqlite);
            loadQueue(db, dbName, sqlite);
        } catch(final SQLException e) {
            market.log.severe("Error while loading:");
            e.printStackTrace();
        }
    }

    private void loadItems(final Database db, final String dbName, final boolean sqlite) throws SQLException{
        items.clear();
        MarketResult res = db.createStatement("SELECT * FROM items").query();
        final Map<Integer, String> sanitizedItems = new HashMap<>();
        final Collection<Integer> unloadable = new ArrayList<>();
        while(res.next()) {
            ItemStack item = null;
            int itemId = -1;
            try {
                itemId = res.getInt(1);
                if (market.mcpcpSupportEnabled()) {
                    item = itemStackFromString(res.getString(2));
                } else {
                    final YamlConfiguration conf = new YamlConfiguration();
                    conf.loadFromString(res.getString(2));
                    item = conf.getItemStack("item");
                }
            } catch(final Throwable e) {
                if (e instanceof InvalidConfigurationException) {
                    // Can sometimes happen if there are crazy characters in an item's lore
                    market.log.warning("Item ID " + itemId + " has invalid characters, the characters will be removed.");
                    final String san = res.getString(2).replaceAll("[\\p{Cc}&&[^\r\n\t]]", "");
                    sanitizedItems.put(itemId, san);
                    try {
                        items.put(itemId, itemStackFromString(san));
                    } catch(final Exception e1) {
                        e1.printStackTrace();
                    }
                }
            } finally {
                if (item != null) {
                    items.put(itemId, item);
                } else {
                    market.log.info(String.format("Item ID %s can no longer be loaded (was it removed from the game?) and will be skipped.", itemId));
                    unloadable.add(itemId);
                }
            }
        }
        for (final Entry<Integer, String> ent : sanitizedItems.entrySet()) {
            db.createStatement("UPDATE listings SET item = ? WHERE id = ?").setString(ent.getValue()).setInt(ent.getKey()).execute();
        }
        for (final int un : unloadable) {
            db.createStatement("DELETE FROM items WHERE id = ?").setInt(un).execute();
        }
        res = sqlite ? db.createStatement("SELECT seq FROM sqlite_sequence WHERE name = ? ").setString("items").query() :
                       db.createStatement("SHOW TABLE STATUS FROM " + dbName + " LIKE ?").setString("items").query();
        if (res.next()) {
            itemIndex = sqlite ? res.getInt(1) + 1 : res.getInt("Auto_increment");
        }
        market.log.info("Item index: " + itemIndex);
    }

    private void loadListings(final Database db, final String dbName, final boolean sqlite) throws SQLException {
        listings.clear();
        final List<Listing> unloadable = new ArrayList<>();
        MarketResult res = db.createStatement("SELECT * FROM listings ORDER BY id ASC").query();
        while(res.next()) {
            final Listing listing = res.constructListing(this);
            final int id = listing.getItemId();
            if (!items.containsKey(id)) {
                market.log.warning(String.format("Item with ID %s has been requested but could not be found.", id));
                unloadable.add(listing);
                continue;
            }
            listings.put(listing.getId(), listing);
        }
        if (!unloadable.isEmpty()) {
            for (final Listing listing : unloadable) {
                db.createStatement("DELETE FROM listings WHERE id = ?").setInt(listing.getId()).execute();
            }
            market.log.warning(String.format("Removed %s listings due to missing items.", unloadable.size()));
            unloadable.clear();
        }
        res = sqlite ? db.createStatement("SELECT seq FROM sqlite_sequence WHERE name = ? ").setString("listings").query() :
            db.createStatement("SHOW TABLE STATUS FROM " + dbName + " LIKE ?").setString("listings").query();
            if (res.next()) {
                listingIndex = sqlite ? res.getInt(1) + 1 : res.getInt("Auto_increment");
            }
            market.log.info("Listing index: " + listingIndex);
        buildCondensed();
    }

    private void loadMail(final Database db, final String dbName, final boolean sqlite) throws SQLException {
        mail.clear();
        final List<Mail> unloadable = new ArrayList<>();
        MarketResult res = db.createStatement("SELECT * FROM mail ORDER BY id ASC").query();
        while(res.next()) {
            final Mail m = res.constructMail(this);
            final int id = m.getItemId();
            if (!items.containsKey(id)) {
                market.log.warning(String.format("Item with ID %s has been requested but could not be found.", id));
                unloadable.add(m);
                continue;
            }
            mail.put(m.getId(), m);
            addWorldItem(m);
        }
        if (!unloadable.isEmpty()) {
            for (final Mail mail : unloadable) {
                db.createStatement("DELETE FROM mail WHERE id = ?").setInt(mail.getId()).execute();
            }
            market.log.warning(String.format("Removed %s mail due to missing items.", unloadable.size()));
            unloadable.clear();
        }
        res = sqlite ? db.createStatement("SELECT seq FROM sqlite_sequence WHERE name = ? ").setString("mail").query() :
                       db.createStatement("SHOW TABLE STATUS FROM " + dbName + " LIKE ?").setString("mail").query();
        if (res.next()) {
            mailIndex = sqlite ? res.getInt(1) + 1 : res.getInt("Auto_increment");
        }
        market.log.info("Mail index: " + mailIndex);
    }

    private void loadQueue(final Database db, final String dbName, final boolean sqlite) throws SQLException {
        queue.clear();
        final List<QueueItem> unloadable = new ArrayList<>();
        MarketResult res = db.createStatement("SELECT * FROM queue ORDER BY id ASC").query();
        final Yaml yaml = new Yaml(new CustomClassLoaderConstructor(Market.class.getClassLoader()));
        while(res.next()) {
            final String q = res.getString("data");
            try {
                final QueueItem item = yaml.loadAs(q, QueueItem.class);
                final int itemId;
                if (item.getMail() != null) {
                    itemId = item.getMail().getItemId();
                } else {
                    itemId = item.getListing().getItemId();
                }
                if (!items.containsKey(itemId)) {
                    market.log.warning(String.format("Item with ID %s has been requested but could not be found.", itemId));
                    unloadable.add(item);
                    continue;
                }
                queue.put(item.getId(), item);
            } catch(final NullPointerException e) {
                market.log.warning("Queue item is corrupt:");
                market.log.warning(q);
            }
        }
        if (!unloadable.isEmpty()) {
            for (final QueueItem item : unloadable) {
                db.createStatement("DELETE FROM queue WHERE id = ?").setInt(item.getId()).execute();
            }
            market.log.warning(String.format("Removed %s items from queue due to missing items.", unloadable.size()));
            unloadable.clear();
        }
        res = sqlite ? db.createStatement("SELECT seq FROM sqlite_sequence WHERE name = ? ").setString("queue").query() :
                       db.createStatement("SHOW TABLE STATUS FROM " + dbName + " LIKE ?").setString("queue").query();
        if (res.next()) {
            queueIndex = sqlite ? res.getInt(1) + 1 : res.getInt("Auto_increment");
        }
        market.log.info("Queue index: " + queueIndex);
    }

    private void addWorldItem(final Listing listing) {
        final String world = listing.getWorld();
        if (!worldListings.containsKey(world)) {
            worldListings.put(world, new TreeSet<>());
        }
        final Set<Listing> listings = worldListings.get(world);
        for (final Listing l : listings) {
            if (l.isStackable(listing)) {
                l.addStacked(listing);
                return;
            }
        }
        listings.add(listing);
    }

    private void addWorldItem(final Mail mailItem) {
        final String world = mailItem.getWorld();
        if (!worldMail.containsKey(world)) {
            worldMail.put(world, new ArrayList<>());
        }
        worldMail.get(world).add(mailItem);
    }

    private List<Listing> getListingsForWorld(final String world) {
        final List<Listing> toReturn = new ArrayList<>();
        if (worldListings.containsKey(world)) {
            toReturn.addAll(new ArrayList<>(worldListings.get(world)));
        }
        for (final String w : market.getLinkedWorlds(world)) {
            if (worldListings.containsKey(w)) {
                toReturn.addAll(new ArrayList<>(worldListings.get(w)));
            }
        }
        return toReturn;
    }

    private Collection<Mail> getMailForWorld(final String world) {
        final Collection<Mail> toReturn = new ArrayList<>();
        if (worldMail.containsKey(world)) {
            toReturn.addAll(worldMail.get(world));
        }
        for (final String w : market.getLinkedWorlds(world)) {
            if (worldMail.containsKey(w)) {
                toReturn.addAll(worldMail.get(w));
            }
        }
        return toReturn;
    }

    public AsyncDatabase getAsyncDb() {
        return asyncDb;
    }

    public Listing queueListing(final String seller, final ItemStack itemStack, final double price, final String world) {
        final int itemId = storeItem(itemStack);
        final long time = System.currentTimeMillis();
        final Listing listing = new Listing(listingIndex, seller, itemId, itemStack.getAmount(), price, world, time);
        listingIndex++;
        final QueueItem item = new QueueItem(queueIndex, time, listing);
        queueIndex++;
        queue.put(item.getId(), item);
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO queue (data) VALUES (?)")
            .setValue(new Yaml().dump(item))
            .setFailureNotice(String.format("Failed to insert queued listing (id: %s, itemId: %s), queue id: %s", listing.getId(), listing.getItemId(), item.getId()))
        );
        return listing;
    }

    public void queueListing(final String seller, final List<ItemStack> items, final double pricePerItem, final String world) {
        final int itemId = storeItem(items.get(0));
        for (final ItemStack itemStack : items) {
            final double price = pricePerItem * itemStack.getAmount();
            final long time = System.currentTimeMillis();
            final Listing listing = new Listing(listingIndex, seller, itemId, itemStack.getAmount(), price, world, time);
            listingIndex++;
            final QueueItem item = new QueueItem(queueIndex, time, listing);
            queueIndex++;
            queueIndex++;
            queue.put(item.getId(), item);
            asyncDb.addStatement(
                new QueuedStatement("INSERT INTO queue (data) VALUES (?)")
                .setValue(new Yaml().dump(item))
                .setFailureNotice(String.format("Failed to insert queued listing (id: %s, itemId: %s), queue id: %s", listing.getId(), listing.getItemId(), item.getId()))
            );
        }
    }

    public Mail queueMail(final String owner, final String from, final ItemStack itemStack, final String world) {
        final int itemId = storeItem(itemStack);
        final Mail mail = new Mail(owner, mailIndex, itemId, itemStack.getAmount(), 0, from, world);
        mailIndex++;
        final QueueItem item = new QueueItem(queueIndex, System.currentTimeMillis(), mail);
        queueIndex++;
        queue.put(item.getId(), item);
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO queue (data) VALUES (?)")
            .setValue(new Yaml().dump(item))
            .setFailureNotice(String.format("Failed to insert queued mail (id: %s, itemId: %s), queue id: %s", mail.getId(), mail.getItemId(), item.getId()))
        );
        return mail;
    }

    public Mail queueMail(final String owner, final String from, final int itemId, final int amount, final String world) {
        final Mail mail = new Mail(owner, mailIndex, itemId, amount, 0, from, world);
        mailIndex++;
        final QueueItem item = new QueueItem(queueIndex, System.currentTimeMillis(), mail);
        queueIndex++;
        queue.put(item.getId(), item);
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO queue (data) VALUES (?)")
            .setValue(new Yaml().dump(item))
            .setFailureNotice(String.format("Failed to insert queued mail (id: %s, itemId: %s), queue id: %s", mail.getId(), mail.getItemId(), item.getId()))
        );
        return mail;
    }

    public Collection<QueueItem> getQueue() {
        return new ArrayList<>(queue.values());
    }

    public void removeItemFromQueue(final int id) {
        final QueueItem item = queue.get(id);
        if (item.getMail() != null) {
            storeMail(item.getMail());
        } else {
            storeListing(item.getListing());
        }
        asyncDb.addStatement(
            new QueuedStatement("DELETE FROM queue WHERE id=?")
            .setValue(id)
            .setFailureNotice(String.format("Problem removing id %s from queue:", id))
        );
        queue.remove(id);
    }

    public static String itemStackToString(final ItemStack item) {
        if (Market.getMarket().mcpcpSupportEnabled()) {
            return CauldronHelper.serialize(item);
        } else {
            final YamlConfiguration conf = new YamlConfiguration();
            final ItemStack toSave = item.clone();
            toSave.setAmount(1);
            conf.set("item", toSave);
            return conf.saveToString();
        }
    }

    public static ItemStack itemStackFromString(final String item) throws InvalidConfigurationException {
        if (Market.getMarket().mcpcpSupportEnabled()) {
            return CauldronHelper.deserialize(item);
        } else {
            final YamlConfiguration conf = new YamlConfiguration();
            conf.loadFromString(item);
            return conf.getItemStack("item");
        }
    }

    public static ItemStack itemStackFromString(final String item, final int amount) {
        if (Market.getMarket().mcpcpSupportEnabled()) {
            final ItemStack stack = CauldronHelper.deserialize(item);
            stack.setAmount(amount);
            return stack;
        } else {
            final YamlConfiguration conf = new YamlConfiguration();
            try {
                conf.loadFromString(item);
                final ItemStack itemStack = conf.getItemStack("item");
                itemStack.setAmount(amount);
                return itemStack;
            } catch (final InvalidConfigurationException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public int storeItem(final ItemStack item) {
        final ItemStack storable = item.clone();
        storable.setAmount(1);
        for (final Entry<Integer, ItemStack> ent : items.entrySet()) {
            if (ent.getValue().equals(storable)) {
                return ent.getKey();
            }
        }
        if (asyncDb.getDb().isSqlite()) {
            asyncDb.addStatement(
                new QueuedStatement("INSERT OR IGNORE INTO items (item) VALUES (?)")
                .setValue(storable)
                .setFailureNotice(String.format("Problem storing ItemStack, should have ID of %s", itemIndex))
            );
        } else {
            final String store = itemStackToString(storable);
            asyncDb.addStatement(
                new QueuedStatement("INSERT INTO items (item) SELECT * FROM (SELECT ?) AS tmp WHERE NOT EXISTS (SELECT item FROM items WHERE item = ?) LIMIT 1;")
                .setValue(store)
                .setValue(store)
                .setFailureNotice(String.format("Problem storing ItemStack, should have ID of %s", itemIndex))
            );
        }

        items.put(itemIndex, storable);
        final int result = itemIndex;
        itemIndex++;
        return result;
    }

    public ItemStack getItem(final int id, final int amount) {
        if (!items.containsKey(id)) {
            market.log.severe("Couldn't find an item with ID " + id);
            return new ItemStack(Material.AIR);
        }
        final ItemStack item = items.get(id).clone();
        item.setAmount(amount);
        return item;
    }

    public Listing createListing(final String seller, final ItemStack item, final double price, final String world) {
        final int itemId = storeItem(item);
        final Long time = System.currentTimeMillis();
        final int id = listingIndex;
        listingIndex++;
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO listings (id, seller, item, amount, price, world, time) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .setValue(id)
            .setValue(seller)
            .setValue(itemId)
            .setValue(item.getAmount())
            .setValue(price)
            .setValue(world)
            .setValue(time)
            .setFailureNotice(String.format("Error on inserting new listing (id: %s, itemId: %s)", id, itemId))
        );
        final Listing listing = new Listing(id, seller, itemId, item.getAmount(), price, world, time);
        listings.put(listing.getId(), listing);
        addWorldItem(listing);
        addToCondensed(listing);
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().updateAllViewers();
        }
        return listing;
    }

    public void createListing(final String seller, final List<ItemStack> items, final double pricePerItem, final String world) {
        final int itemId = storeItem(items.get(0));
        for (final ItemStack item : items) {
            final double price = pricePerItem * item.getAmount();
            final Long time = System.currentTimeMillis();
            final int id = listingIndex;
            listingIndex++;
            asyncDb.addStatement(
                new QueuedStatement("INSERT INTO listings (id, seller, item, amount, price, world, time) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .setValue(id)
                .setValue(seller)
                .setValue(itemId)
                .setValue(item.getAmount())
                .setValue(price)
                .setValue(world)
                .setValue(time)
                .setFailureNotice(String.format("Error on inserting new listing (id: %s, itemId: %s)", id, itemId))
            );
            final Listing listing = new Listing(id, seller, itemId, item.getAmount(), price, world, time);
            listings.put(listing.getId(), listing);
            addWorldItem(listing);
            addToCondensed(listing);
        }
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().updateAllViewers();
        }
    }

    public void storeListing(final Listing listing) {
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO listings (id, seller, item, amount, price, world, time) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .setValue(listing.getId())
            .setValue(listing.getSeller())
            .setValue(listing.getItemId())
            .setValue(listing.getAmount())
            .setValue(listing.getPrice())
            .setValue(listing.getWorld())
            .setValue(listing.getTime())
            .setFailureNotice(String.format("Error on inserting listing from Queue (id: %s, itemId: %s)", listing.getId(), listing.getItemId()))
        );
        listings.put(listing.getId(), listing);
        addWorldItem(listing);
        addToCondensed(listing);
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.notifyPlayer(listing.getSeller(), market.getLocale().get("your_listing_has_been_added", market.getItemName(getItem(listing.getItemId(), listing.getAmount()))));
            market.getInterfaceHandler().updateAllViewers();
        }
    }

    public void storeMail(final Mail m) {
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO mail (id, owner, item, amount, sender, world, pickup) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .setValue(m.getId())
            .setValue(m.getOwner())
            .setValue(m.getItemId())
            .setValue(m.getAmount())
            .setValue(m.getSender())
            .setValue(m.getWorld())
            .setValue(m.getPickup())
            .setFailureNotice(String.format("Error on inserting mail from Queue (id: %s, itemId: %s)", m.getId(), m.getItemId()))
        );
        mail.put(m.getId(), m);
        addWorldItem(m);
        market.notifyPlayer(m.getOwner(), market.getLocale().get("you_have_new_mail"));
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().refreshViewer(m.getOwner(), "Mail");
        }
    }

    public Listing getListing(final int id) {
        if (listings.containsKey(id)) {
            return listings.get(id);
        }
        return null;
    }

    public List<Listing> getListings(final String viewer, final SortMethod sort, final String world) {
        final List<Listing> list = market.enableMultiworld() ? getListingsForWorld(world) : new ArrayList<>(condensedListings);
        switch(sort) {
            default:
                Collections.sort(list, Listing.Comparators.RECENT);
                break;
            case DEFAULT:
                Collections.sort(list, Listing.Comparators.RECENT);
                break;
            case PRICE_HIGHEST:
                Collections.sort(list, Listing.Comparators.PRICE_HIGHEST);
                break;
            case PRICE_LOWEST:
                Collections.sort(list, Listing.Comparators.PRICE_LOWEST);
                break;
            case AMOUNT_HIGHEST:
                Collections.sort(list, Listing.Comparators.AMOUNT_HIGHEST);
                break;
        }
        return list;
    }

    private void buildCondensed() {
        for (final Listing listing : Lists.reverse(new ArrayList<>(listings.values()))) {
            for (final Listing l : condensedListings) {
                if (l.isStackable(listing)) {
                    l.addStacked(listing);
                    break;
                }
            }
            condensedListings.add(listing);
            addWorldItem(listing);
        }
    }

    private void addToCondensed(final Listing listing) {
        for (final Listing l : condensedListings) {
            if (l.isStackable(listing)) {
                l.addStacked(listing);
                return;
            }
        }
        condensedListings.add(listing);
    
        //noinspection StatementWithEmptyBody
        if (market.getChat() != null) {
            // Don't run this if we're importing...
            /*if (market.announceOnCreate()) {
                ItemStack created = getItem(listing.getItemId(), 1);
                market.getChat().announce(new TellRawMessage().setText(market.getLocale().get("listing_created.prefix1"))
                        .setColor(market.getLocale().get("listing_created.prefix1_color"))
                        .setExtra(
                        new TellRawMessage[] {
                            new TellRawMessage().setText(market.getLocale().get("listing_created.prefix2")).setBold(true)
                            .setColor(market.getLocale().get("listing_created.prefix2_color")),

                            new TellRawMessage().setText(market.getLocale().get("listing_created.prefix3")).setBold(false)
                            .setColor(market.getLocale().get("listing_created.prefix3_color")),

                            new TellRawMessage().setText(market.getLocale().get("listing_created.main"))
                            .setColor(market.getLocale().get("listing_created.main_color"))
                            .setExtra(
                                new TellRawMessage[] {
                                    new TellRawMessage()
                                    .setText(market.getLocale().get("listing_created.item", market.getItemName(created)))
                                    .setColor(market.getLocale().get("listing_created.item_color"))
                                    .setHover(new TellRawHoverEvent()
                                            .setAction(TellRawHoverEvent.ACTION_SHOW_ITEM)
                                            .setValue(market.getChat().jsonStack(created)))
                                    .setClick(new TellRawClickEvent()
                                            .setAction(TellRawClickEvent.ACTION_RUN_COMMAND)
                                            .setValue("/market listings " + listing.getId())),

                                    new TellRawMessage()
                                    .setText(market.getLocale().get("listing_created.suffix"))
                                    .setColor(market.getLocale().get("listing_created.suffix_color"))
                            }
                        )
                    })
                , "globalmarket.seeannounce");
            }*/
        }
    }

    private void removeFromCondensed(final Listing listing) {
        Iterator<Listing> it = condensedListings.iterator();
        Listing sibling = null;
        while(it.hasNext()) {
            final Listing l = it.next();
            if (l.getId() == listing.getId()) {
                it.remove();
                if (!l.getStacked().isEmpty()) {
                    final Listing n = l.getStacked().get(0);
                    l.getStacked().remove(n);
                    n.setStacked(new ArrayList<>(l.getStacked()));
                    sibling = n;
                }
                break;
            }
        }
        if (sibling != null) {
            condensedListings.add(sibling);
        }
        if (worldListings.containsKey(listing.getWorld())) {
            final Set<Listing> world = worldListings.get(listing.getWorld());
            it = world.iterator();
            while(it.hasNext()) {
                final Listing l = it.next();
                if (l.getId() == listing.getId()) {
                    it.remove();
                    break;
                }
            }
            if (sibling != null) {
                world.add(sibling);
            }
        }
    }

    public Iterable<Listing> getOwnedListings(final String world, final String name) {
        final Collection<Listing> list = new ArrayList<>();
        for (final Listing listing : market.enableMultiworld() ? getListingsForWorld(world) : listings.values()) {
            if (listing.getSeller().equalsIgnoreCase(name)) {
                list.add(listing);
            }
        }
        return list;
    }

    public List<Listing> getAllListings() {
        return new ArrayList<>(listings.values());
    }

    @SuppressWarnings("deprecation")
    public SearchResult getListings(final String viewer, final SortMethod sort, final String search, final String world) {
        final List<Listing> found = new ArrayList<>();
        final List<Listing> list = market.enableMultiworld() ? getListingsForWorld(world) : new ArrayList<>(condensedListings);
        for (final Listing listing : list) {
            final ItemStack item = getItem(listing.getItemId(), listing.getAmount());
            final String itemName = market.getItemName(item);
            if (itemName.toLowerCase().contains(search.toLowerCase())
                    || isItemId(search, item.getTypeId())
                    || isInDisplayName(search.toLowerCase(), item)
                    || isInEnchants(search.toLowerCase(), item)
                    || isInLore(search.toLowerCase(), item)
                    || search.equalsIgnoreCase(Integer.toString(listing.getId()))
                    || listing.seller.toLowerCase().contains(search.toLowerCase())) {
                found.add(listing);
            }
        }
        switch(sort) {
            default:
                break;
            case PRICE_HIGHEST:
                Collections.sort(found, Listing.Comparators.PRICE_HIGHEST);
                break;
            case PRICE_LOWEST:
                Collections.sort(found, Listing.Comparators.PRICE_LOWEST);
                break;
            case AMOUNT_HIGHEST:
                Collections.sort(found, Listing.Comparators.AMOUNT_HIGHEST);
                break;
        }
        return new SearchResult(found.size(), found);
    }

    public void removeListing(final int id) {
    	listings.remove(id);
        condensedListings.clear();
        buildCondensed();
        asyncDb.addStatement(
            new QueuedStatement("DELETE FROM listings WHERE id=?")
            .setValue(id)
            .setFailureNotice(String.format("Error removing listing id %s", id))
        );
    }

    public int getNumListings(final String world) {
        return market.enableMultiworld() ? getListingsForWorld(world).size() : condensedListings.size();
    }

    public int getNumListingsFor(final String name, final String world) {
        int amount = 0;
        for (final Listing listing : market.enableMultiworld() ? getListingsForWorld(world) : new ArrayList<>(condensedListings)) {
            if (listing.getSeller().equalsIgnoreCase(name)) {
                amount++;
            }
        }
        return amount;
    }

    public Map<Integer, Listing> getCachedListingIndex() {
        return listings;
    }

    public Mail createMail(final String owner, final String from, final int itemId, final int amount, final String world) {
        final int id = mailIndex;
        mailIndex++;
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO mail (id, owner, item, amount, sender, world, pickup) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .setValue(id)
            .setValue(owner)
            .setValue(itemId)
            .setValue(amount)
            .setValue(from)
            .setValue(world)
            .setValue(0)
            .setFailureNotice(String.format("Error on inserting new mail (id: %s, itemId: %s)", id, itemId))
        );
        final Mail m = new Mail(owner, id, itemId, amount, 0, from, world);
        mail.put(m.getId(), m);
        addWorldItem(m);
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().refreshViewer(m.getOwner(), "Mail");
        }
        return m;
    }

    public Mail createMail(final String owner, final String from, final ItemStack item, final double pickup, final String world) {
        final int itemId = storeItem(item);
        final int id = mailIndex;
        mailIndex++;
        asyncDb.addStatement(
            new QueuedStatement("INSERT INTO mail (id, owner, item, amount, sender, world, pickup) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .setValue(id)
            .setValue(owner)
            .setValue(itemId)
            .setValue(item.getAmount())
            .setValue(from)
            .setValue(world)
            .setValue(pickup)
            .setFailureNotice(String.format("Error on inserting new mail (id: %s, itemId: %s)", id, itemId))
        );
        final Mail m = new Mail(owner, id, itemId, item.getAmount(), pickup, from, world);
        mail.put(m.getId(), m);
        addWorldItem(m);
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().refreshViewer(m.getOwner(), "Mail");
        }
        return m;
    }

    public void storePayment(final ItemStack item, final String player, final String buyer, final double fullAmount, final double amount, final double cut, final String world) {
        ItemStack book = new ItemStack(Material.BOOK_AND_QUILL);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            meta = (BookMeta) market.getServer().getItemFactory().getItemMeta(book.getType());
        }
        meta.setDisplayName(market.getLocale().get("transaction_log.item_name"));
        final String itemName = market.getItemName(item);
        final String logStr = market.getLocale().get("transaction_log.title") + "\n\n" +
                        market.getLocale().get("transaction_log.item_sold", itemName) + "\n\n" +
                        market.getLocale().get("transaction_log.buyer", buyer) + "\n\n" +
                        market.getLocale().get("transaction_log.sale_price", fullAmount) + "\n\n" +
                        market.getLocale().get("transaction_log.market_cut", cut) +  "\n\n" +
                        market.getLocale().get("transaction_log.amount_recieved", amount);
        meta.setPages(logStr);
        book.setItemMeta(meta);
        if (market.mcpcpSupportEnabled()) {
            book = CauldronHelper.wrapItemStack(book);
        }
        createMail(player, buyer, book, amount, world);
    }

    public IMarketItem getMail(final int id) {
        if (mail.containsKey(id)) {
            return mail.get(id);
        }
        return null;
    }

    public List<Mail> getMail(final String owner, final String world, final SortMethod sort) {
        final List<Mail> activeListings = new ArrayList<>();
        for (final Listing listing : getOwnedListings(world, owner)) {
            activeListings.add(new Mail(owner, -listing.getId(), listing.getItemId(), listing.getAmount(), 0, null, world));
        }
        final Collection<Mail> ownedMail = Collections2.filter(market.enableMultiworld() ? getMailForWorld(world) : mail.values(), mail1 -> mail1.getOwner().equals(owner));
        final List<Mail> list;
        if (sort == SortMethod.LISTINGS_ONLY) {
            list = activeListings;
        } else if (sort == SortMethod.MAIL_ONLY) {
            list = Lists.reverse(new ArrayList<>(ownedMail));
        } else {
            list = Lists.reverse(new ArrayList<>(ownedMail));
            list.addAll(activeListings);
        }
        return list;
    }

    public void nullifyMailPayment(final int id) {
        asyncDb.addStatement(
            new QueuedStatement("UPDATE mail SET pickup=? WHERE id=?")
            .setValue(0)
            .setValue(id)
            .setFailureNotice(String.format("Error nullifying mail payment with id %s", id))
        );
        if (mail.containsKey(id)) {
            final Mail m = mail.get(id);
            m.setPickup(0);
            if (market.getInterfaceHandler() != null) {
                // This will be null if the importer is running
                market.getInterfaceHandler().refreshViewer(m.getOwner(), "Mail");
            }
        }
    }

    public void removeMail(final int id) {
        final Mail m = mail.get(id);
        mail.remove(id);
        worldMail.get(m.getWorld()).remove(m);
        asyncDb.addStatement(
            new QueuedStatement("DELETE FROM mail WHERE id=?")
            .setValue(id)
            .setFailureNotice(String.format("Error removing mail id %s", id))
        );
        if (market.getInterfaceHandler() != null) {
            // This will be null if the importer is running
            market.getInterfaceHandler().refreshViewer(m.getOwner(), "Mail");
        }
    }

    public int getNumMail(final String player, final String world, final boolean listings) {
        final Collection<Mail> ownedMail = Collections2.filter(market.enableMultiworld() ? getMailForWorld(world) : mail.values(), mail1 -> mail1.getOwner().equals(player));
        return ownedMail.size() + (listings ? getNumListingsFor(player, world) : 0);
    }

    /*
     * Basic search method
     */
    public boolean isItemId(final String search, final int typeId) {
        return search.equalsIgnoreCase(Integer.toString(typeId));
    }

    /*
     * Basic search method
     */
    public boolean isInDisplayName(final CharSequence search, final ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().toLowerCase().contains(search);
    }

    /*
     * Basic search method
     */
    public boolean isInEnchants(final CharSequence search, final ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            for (final Entry<Enchantment, Integer> entry : item.getItemMeta().getEnchants().entrySet()) {
                if (entry.getKey().getName().toLowerCase().contains(search)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Basic search method
     */
    public boolean isInLore(final CharSequence search, final ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (final String l : item.getItemMeta().getLore()) {
                if (l.toLowerCase().contains(search)) {
                    return true;
                }
            }
        }
        return false;
    }
}
