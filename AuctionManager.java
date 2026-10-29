package com.example.auction;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Хранит лоты, проводит покупки и сохраняет всё в listings.yml. Вызывать только из главного потока. */
public final class AuctionManager {

    public enum BuyStatus { OK, NOT_FOUND, OWN, NO_ECONOMY, NO_MONEY, FAILED }

    private final AuctionPlugin plugin;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final File file;

    private final Object ioLock = new Object();
    private long saveSeq = 0;      // главный поток
    private long lastWritten = 0;  // под ioLock

    public AuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "listings.yml");
    }

    // ---------- Загрузка / сохранение ----------

    public void load() {
        listings.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("listings");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item", "")));
                listings.put(id, new Listing(
                        id,
                        UUID.fromString(s.getString("seller", "")),
                        s.getString("seller-name", "?"),
                        item,
                        s.getDouble("price"),
                        s.getLong("created"),
                        s.getLong("expires")
                ));
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось загрузить лот " + key + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Загружено лотов: " + listings.size());
    }

    private String snapshot() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String p = "listings." + l.id();
            yml.set(p + ".seller", l.seller().toString());
            yml.set(p + ".seller-name", l.sellerName());
            yml.set(p + ".price", l.price());
            yml.set(p + ".created", l.createdAt());
            yml.set(p + ".expires", l.expiresAt());
            yml.set(p + ".item", Base64.getEncoder().encodeToString(l.item().serializeAsBytes()));
        }
        return yml.saveToString();
    }

    public void saveAsync() {
        final long seq = ++saveSeq;
        final String data = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(seq, data));
    }

    public void saveNow() {
        write(++saveSeq, snapshot());
    }

    private void write(long seq, String data) {
        synchronized (ioLock) {
            if (seq < lastWritten) return; // более новый снимок уже записан
            lastWritten = seq;
            try {
                Path target = file.toPath();
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling("listings.yml.tmp");
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось сохранить listings.yml: " + e.getMessage());
            }
        }
    }

    // ---------- Запросы ----------

    public Listing get(UUID id) {
        return listings.get(id);
    }

    /** Активные лоты, новые первыми. */
    public List<Listing> active() {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .filter(l -> !l.expired(now))
                .sorted(Comparator.comparingLong(Listing::createdAt).reversed())
                .toList();
    }

    /** Лоты игрока: сначала активные, затем истёкшие. */
    public List<Listing> of(UUID seller) {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .filter(l -> l.seller().equals(seller))
                .sorted(Comparator.comparing((Listing l) -> l.expired(now))
                        .thenComparing(Comparator.comparingLong(Listing::createdAt).reversed()))
                .toList();
    }

    public int countOf(UUID seller) {
        return (int) listings.values().stream().filter(l -> l.seller().equals(seller)).count();
    }

    public int expiredCount(UUID seller) {
        long now = System.currentTimeMillis();
        return (int) listings.values().stream()
                .filter(l -> l.seller().equals(seller) && l.expired(now))
                .count();
    }

    // ---------- Действия ----------

    public Listing add(Player seller, ItemStack item, double price) {
        long now = System.currentTimeMillis();
        long duration = plugin.getConfig().getLong("listing-duration-hours", 48) * 3_600_000L;
        Listing l = new Listing(UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                item.clone(), price, now, now + duration);
        listings.put(l.id(), l);
        saveAsync();
        return l;
    }

    /** Снять свой лот (активный или истёкший) и вернуть предмет. */
    public boolean takeBack(Player player, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller().equals(player.getUniqueId())) return false;
        listings.remove(id);
        saveAsync();
        give(player, l.item());
        return true;
    }

    public double payout(double price) {
        double tax = plugin.getConfig().getDouble("tax-percent", 0.0);
        double result = price * (1.0 - tax / 100.0);
        return Math.round(result * 100.0) / 100.0;
    }

    public BuyStatus buy(Player buyer, UUID id) {
        Listing l = listings.get(id);
        long now = System.currentTimeMillis();
        if (l == null || l.expired(now)) return BuyStatus.NOT_FOUND;
        if (l.seller().equals(buyer.getUniqueId())) return BuyStatus.OWN;

        Economy eco = plugin.economy();
        if (eco == null) return BuyStatus.NO_ECONOMY;
        if (!eco.has(buyer, l.price())) return BuyStatus.NO_MONEY;

        EconomyResponse withdraw = eco.withdrawPlayer(buyer, l.price());
        if (!withdraw.transactionSuccess()) return BuyStatus.FAILED;

        // Лот удаляем сразу после списания денег — двойная покупка невозможна (главный поток)
        listings.remove(id);
        saveAsync();

        double payout = payout(l.price());
        OfflinePlayer seller = Bukkit.getOfflinePlayer(l.seller());
        EconomyResponse deposit = eco.depositPlayer(seller, payout);
        if (!deposit.transactionSuccess()) {
            plugin.getLogger().severe("Не удалось выплатить " + payout + " продавцу " + l.sellerName()
                    + " (" + l.seller() + ") за лот " + l.id() + ": " + deposit.errorMessage);
        }

        give(buyer, l.item());

        Player onlineSeller = Bukkit.getPlayer(l.seller());
        if (onlineSeller != null) {
            onlineSeller.sendMessage(plugin.msg("sold",
                    Placeholder.unparsed("buyer", buyer.getName()),
                    Placeholder.component("item", l.item().displayName()),
                    Placeholder.unparsed("amount", String.valueOf(l.item().getAmount())),
                    Placeholder.unparsed("payout", plugin.fmt(payout)),
                    Placeholder.unparsed("tax", String.valueOf(plugin.getConfig().getDouble("tax-percent", 0.0)))));
        }
        return BuyStatus.OK;
    }

    /** Выдать предмет; если инвентарь полон — выбросить под ноги. */
    public void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
        if (!left.isEmpty()) {
            left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            player.sendMessage(plugin.msg("inventory-full"));
        }
    }
}
