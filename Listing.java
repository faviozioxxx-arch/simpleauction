package com.example.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Один лот на аукционе. */
public record Listing(
        UUID id,
        UUID seller,
        String sellerName,
        ItemStack item,
        double price,
        long createdAt,
        long expiresAt
) {
    public boolean expired(long now) {
        return now >= expiresAt;
    }
}
