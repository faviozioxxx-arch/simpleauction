package com.example.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AuctionPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AuctionManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new AuctionManager(this);
        manager.load();

        AuctionCommand command = new AuctionCommand(this);
        PluginCommand ah = Objects.requireNonNull(getCommand("ah"), "command ah missing in plugin.yml");
        ah.setExecutor(command);
        ah.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // Провайдер экономики может зарегистрироваться после нас — проверяем после старта сервера
        getServer().getScheduler().runTask(this, () -> {
            if (economy() == null) {
                getLogger().warning("Не найден провайдер экономики Vault (EssentialsX, CMI и т.п.). Покупки не будут работать.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveNow();
        }
    }

    public AuctionManager manager() {
        return manager;
    }

    /** Экономика Vault; может быть null, если провайдера нет. */
    public Economy economy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public String fmt(double value) {
        Economy eco = economy();
        return eco != null ? eco.format(value) : String.format(Locale.US, "%.2f", value);
    }

    /** Сообщение из messages.* с префиксом. */
    public Component msg(String key, TagResolver... resolvers) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, "<red>missing message: " + key);
        return MM.deserialize(prefix + body, resolvers);
    }

    /** Строка конфига по полному пути (без префикса). */
    public Component raw(String path, TagResolver... resolvers) {
        return MM.deserialize(getConfig().getString(path, "<red>missing: " + path), resolvers);
    }

    public List<Component> rawList(String path, TagResolver... resolvers) {
        return getConfig().getStringList(path).stream()
                .map(line -> MM.deserialize(line, resolvers))
                .toList();
    }

    public String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        long d = minutes / 1440;
        long h = (minutes % 1440) / 60;
        long m = minutes % 60;
        if (d > 0) return d + "д " + h + "ч";
        if (h > 0) return h + "ч " + m + "м";
        return m + "м";
    }
}
