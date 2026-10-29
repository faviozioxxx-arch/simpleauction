package com.example.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class GuiUtil {

    private GuiUtil() {}

    static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static ItemStack filler() {
        ItemStack it = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        it.editMeta(meta -> {
            meta.displayName(Component.empty());
            meta.setHideTooltip(true);
        });
        return it;
    }

    /** Кнопка с именем из конфига (путь вроде "gui.btn-prev"). */
    static ItemStack button(AuctionPlugin plugin, Material material, String path, TagResolver... resolvers) {
        ItemStack it = ItemStack.of(material);
        Component name = noItalic(plugin.raw(path, resolvers));
        it.editMeta(meta -> meta.displayName(name));
        return it;
    }

    /** Копия предмета с добавленным лором из конфига (сохраняя оригинальный лор). */
    static ItemStack withLore(AuctionPlugin plugin, ItemStack base, String lorePath, TagResolver... resolvers) {
        ItemStack it = base.clone();
        List<Component> lore = new ArrayList<>();
        List<Component> existing = it.lore();
        if (existing != null) lore.addAll(existing);
        for (Component line : plugin.rawList(lorePath, resolvers)) {
            lore.add(noItalic(line));
        }
        it.lore(lore);
        return it;
    }
}
