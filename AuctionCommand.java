package com.example.auction;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionPlugin plugin;

    public AuctionCommand(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("auction.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        if (!player.hasPermission("auction.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            new AuctionGUI(plugin, player, AuctionGUI.Mode.ALL, 0).open();
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell", "s" -> sell(player, args);
            case "my", "mine" -> new AuctionGUI(plugin, player, AuctionGUI.Mode.MINE, 0).open();
            default -> plugin.rawList("messages.help").forEach(player::sendMessage);
        }
        return true;
    }

    private void sell(Player player, String[] args) {
        if (!player.hasPermission("auction.sell")) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("usage-sell"));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-price"));
            return;
        }
        if (!Double.isFinite(price)) {
            player.sendMessage(plugin.msg("invalid-price"));
            return;
        }
        price = Math.round(price * 100.0) / 100.0;

        double min = plugin.getConfig().getDouble("min-price", 1.0);
        double max = plugin.getConfig().getDouble("max-price", 1_000_000_000.0);
        if (price < min || price > max) {
            player.sendMessage(plugin.msg("price-range",
                    Placeholder.unparsed("min", plugin.fmt(min)),
                    Placeholder.unparsed("max", plugin.fmt(max))));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.msg("hold-item"));
            return;
        }

        String typeName = hand.getType().name();
        for (String banned : plugin.getConfig().getStringList("blacklist")) {
            if (banned.equalsIgnoreCase(typeName)) {
                player.sendMessage(plugin.msg("blacklisted"));
                return;
            }
        }

        int limit = plugin.getConfig().getInt("max-listings-per-player", 10);
        if (plugin.manager().countOf(player.getUniqueId()) >= limit) {
            player.sendMessage(plugin.msg("limit-reached", Placeholder.unparsed("limit", String.valueOf(limit))));
            return;
        }

        ItemStack toSell = hand.clone();
        player.getInventory().setItemInMainHand(null);
        plugin.manager().add(player, toSell, price);

        player.sendMessage(plugin.msg("listed",
                Placeholder.component("item", toSell.displayName()),
                Placeholder.unparsed("amount", String.valueOf(toSell.getAmount())),
                Placeholder.unparsed("price", plugin.fmt(price)),
                Placeholder.unparsed("hours", String.valueOf(plugin.getConfig().getLong("listing-duration-hours", 48)))));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("sell", "my", "help"));
            if (sender.hasPermission("auction.admin")) options.add("reload");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
