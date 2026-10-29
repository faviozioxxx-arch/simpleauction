package com.example.auction;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Окно подтверждения покупки. */
public final class ConfirmGUI implements InventoryHolder {

    public static final int SLOT_YES = 11;
    public static final int SLOT_ITEM = 13;
    public static final int SLOT_NO = 15;

    private final UUID listingId;
    private final int returnPage;
    private final Inventory inventory;

    public ConfirmGUI(AuctionPlugin plugin, Listing listing, int returnPage) {
        this.listingId = listing.id();
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 27, plugin.raw("gui.title-confirm"));

        ItemStack filler = GuiUtil.filler();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        String price = plugin.fmt(listing.price());

        inventory.setItem(SLOT_ITEM, GuiUtil.withLore(plugin, listing.item(), "gui.lore-confirm",
                Placeholder.unparsed("price", price),
                Placeholder.unparsed("seller", listing.sellerName())));
        inventory.setItem(SLOT_YES, GuiUtil.button(plugin, Material.LIME_STAINED_GLASS_PANE, "gui.btn-yes",
                Placeholder.unparsed("price", price)));
        inventory.setItem(SLOT_NO, GuiUtil.button(plugin, Material.RED_STAINED_GLASS_PANE, "gui.btn-no"));
    }

    public UUID listingId() { return listingId; }
    public int returnPage() { return returnPage; }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
