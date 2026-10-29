package com.example.auction;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Главное окно аукциона: 45 слотов лотов + нижняя панель управления. */
public final class AuctionGUI implements InventoryHolder {

    public enum Mode { ALL, MINE }

    public static final int PAGE_SIZE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_MODE = 47;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_REFRESH = 51;
    public static final int SLOT_NEXT = 53;

    private final AuctionPlugin plugin;
    private final Player player;
    private final Mode mode;
    private final int page;
    private final int pages;
    private final Map<Integer, UUID> slotMap = new HashMap<>();
    private final Inventory inventory;

    public AuctionGUI(AuctionPlugin plugin, Player player, Mode mode, int requestedPage) {
        this.plugin = plugin;
        this.player = player;
        this.mode = mode;

        AuctionManager manager = plugin.manager();
        List<Listing> all = mode == Mode.ALL ? manager.active() : manager.of(player.getUniqueId());

        this.pages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));

        this.inventory = Bukkit.createInventory(this, 54, plugin.raw(
                mode == Mode.ALL ? "gui.title-all" : "gui.title-mine",
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages))));

        fill(all);
    }

    private void fill(List<Listing> all) {
        ItemStack filler = GuiUtil.filler();
        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        long now = System.currentTimeMillis();
        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Listing l = all.get(i);
            int slot = i - from;
            slotMap.put(slot, l.id());
            inventory.setItem(slot, display(l, now));
        }

        TagResolver pageInfo = TagResolver.resolver(
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages)));

        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.button(plugin, Material.ARROW, "gui.btn-prev"));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.button(plugin, Material.ARROW, "gui.btn-next"));
        }
        inventory.setItem(SLOT_INFO, GuiUtil.button(plugin, Material.PAPER, "gui.btn-info", pageInfo));
        inventory.setItem(SLOT_REFRESH, GuiUtil.button(plugin, Material.CLOCK, "gui.btn-refresh"));

        if (mode == Mode.ALL) {
            inventory.setItem(SLOT_MODE, GuiUtil.button(plugin, Material.CHEST, "gui.btn-mine",
                    Placeholder.unparsed("count", String.valueOf(plugin.manager().countOf(player.getUniqueId()))),
                    Placeholder.unparsed("limit", String.valueOf(plugin.getConfig().getInt("max-listings-per-player", 10)))));
        } else {
            inventory.setItem(SLOT_MODE, GuiUtil.button(plugin, Material.EMERALD, "gui.btn-all"));
        }
    }

    private ItemStack display(Listing l, long now) {
        String lorePath = mode == Mode.ALL
                ? "gui.lore-all"
                : (l.expired(now) ? "gui.lore-expired" : "gui.lore-mine");
        return GuiUtil.withLore(plugin, l.item(), lorePath,
                Placeholder.unparsed("price", plugin.fmt(l.price())),
                Placeholder.unparsed("seller", l.sellerName()),
                Placeholder.unparsed("time", plugin.duration(l.expiresAt() - now)));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public UUID listingAt(int slot) {
        return slotMap.get(slot);
    }

    public Mode mode() { return mode; }
    public int page() { return page; }
    public int pages() { return pages; }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
