package com.example.auction;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class GuiListener implements Listener {

    private final AuctionPlugin plugin;

    public GuiListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isOurs(InventoryHolder holder) {
        return holder instanceof AuctionGUI || holder instanceof ConfirmGUI;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!isOurs(holder)) return;

        e.setCancelled(true); // нельзя ничего вытаскивать/закидывать/шифтить
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();
        if (holder instanceof AuctionGUI gui) {
            handleMain(player, gui, slot);
        } else if (holder instanceof ConfirmGUI confirm) {
            handleConfirm(player, confirm, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (isOurs(e.getView().getTopInventory().getHolder())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int expired = plugin.manager().expiredCount(player.getUniqueId());
            if (expired > 0) {
                player.sendMessage(plugin.msg("expired-notice",
                        Placeholder.unparsed("count", String.valueOf(expired))));
            }
        }, 60L);
    }

    // ---------- Главное окно ----------

    private void handleMain(Player player, AuctionGUI gui, int slot) {
        if (slot == AuctionGUI.SLOT_PREV) {
            if (gui.page() > 0) reopen(player, gui.mode(), gui.page() - 1);
            return;
        }
        if (slot == AuctionGUI.SLOT_NEXT) {
            if (gui.page() < gui.pages() - 1) reopen(player, gui.mode(), gui.page() + 1);
            return;
        }
        if (slot == AuctionGUI.SLOT_MODE) {
            reopen(player, gui.mode() == AuctionGUI.Mode.ALL ? AuctionGUI.Mode.MINE : AuctionGUI.Mode.ALL, 0);
            return;
        }
        if (slot == AuctionGUI.SLOT_REFRESH) {
            reopen(player, gui.mode(), gui.page());
            return;
        }

        UUID id = gui.listingAt(slot);
        if (id == null) return;

        AuctionManager manager = plugin.manager();
        Listing listing = manager.get(id);
        if (listing == null) {
            player.sendMessage(plugin.msg("not-found"));
            reopen(player, gui.mode(), gui.page());
            return;
        }

        if (gui.mode() == AuctionGUI.Mode.ALL) {
            if (listing.expired(System.currentTimeMillis())) {
                player.sendMessage(plugin.msg("not-found"));
                reopen(player, gui.mode(), gui.page());
                return;
            }
            if (listing.seller().equals(player.getUniqueId())) {
                player.sendMessage(plugin.msg("own-listing"));
                return;
            }
            int page = gui.page();
            later(() -> player.openInventory(new ConfirmGUI(plugin, listing, page).getInventory()));
        } else {
            if (manager.takeBack(player, id)) {
                player.sendMessage(plugin.msg("returned"));
            }
            reopen(player, AuctionGUI.Mode.MINE, gui.page());
        }
    }

    // ---------- Подтверждение ----------

    private void handleConfirm(Player player, ConfirmGUI confirm, int slot) {
        if (slot == ConfirmGUI.SLOT_NO) {
            reopen(player, AuctionGUI.Mode.ALL, confirm.returnPage());
            return;
        }
        if (slot != ConfirmGUI.SLOT_YES) return;

        AuctionManager manager = plugin.manager();
        Listing listing = manager.get(confirm.listingId());
        if (listing == null) {
            player.sendMessage(plugin.msg("not-found"));
            reopen(player, AuctionGUI.Mode.ALL, confirm.returnPage());
            return;
        }

        var price = Placeholder.unparsed("price", plugin.fmt(listing.price()));
        AuctionManager.BuyStatus status = manager.buy(player, confirm.listingId());
        switch (status) {
            case OK -> {
                player.sendMessage(plugin.msg("bought", price,
                        Placeholder.component("item", listing.item().displayName()),
                        Placeholder.unparsed("amount", String.valueOf(listing.item().getAmount()))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            case NOT_FOUND -> player.sendMessage(plugin.msg("not-found"));
            case OWN -> player.sendMessage(plugin.msg("own-listing"));
            case NO_MONEY -> player.sendMessage(plugin.msg("no-money", price));
            case NO_ECONOMY -> player.sendMessage(plugin.msg("no-economy"));
            case FAILED -> player.sendMessage(plugin.msg("tx-failed"));
        }
        reopen(player, AuctionGUI.Mode.ALL, confirm.returnPage());
    }

    // ---------- Утилиты ----------

    /** Открывать/закрывать инвентари из обработчика клика безопаснее на следующем тике. */
    private void later(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private void reopen(Player player, AuctionGUI.Mode mode, int page) {
        later(() -> new AuctionGUI(plugin, player, mode, page).open());
    }
}
