package com.bap80.merchantrole;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MerchantRolePlugin extends JavaPlugin implements Listener {
    private static final Pattern SAFE_GROUP = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private final Set<UUID> completed = new HashSet<>();
    private String group;
    private String region;
    private int requiredCustomers;
    private int requiredEmeraldBlocks;

    @Override public void onEnable() {
        saveDefaultConfig();
        group = getConfig().getString("luckperms-group", "merchant");
        region = getConfig().getString("worldguard-region", "market");
        requiredCustomers = Math.max(1, getConfig().getInt("required-distinct-customers", 5));
        requiredEmeraldBlocks = Math.max(1, getConfig().getInt("emerald-blocks", 64));
        if (!SAFE_GROUP.matcher(group).matches()) {
            getLogger().severe("Invalid LuckPerms group name in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        loadCompleted();
        registerQuickShopSuccessEvent();
        Bukkit.getScheduler().runTaskTimer(this, this::checkOnlinePlayers, 20L, 20L);
        getLogger().info("Merchant role task active: region=" + region + ", distinct customers=" + requiredCustomers);
    }

    private void registerQuickShopSuccessEvent() {
        try {
            Class<?> raw = Class.forName("com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent");
            if (!Event.class.isAssignableFrom(raw)) throw new IllegalStateException("Not a Bukkit event");
            @SuppressWarnings("unchecked") Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            EventExecutor executor = (listener, event) -> recordSale(event);
            getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, this, true);
        } catch (ClassNotFoundException e) {
            getLogger().severe("QuickShop-Hikari not found or incompatible; install it and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void recordSale(Event event) {
        try {
            Object shop = event.getClass().getMethod("getShop").invoke(event);
            Object purchaser = event.getClass().getMethod("getPurchaser").invoke(event);
            Object owner = shop.getClass().getMethod("getOwner").invoke(shop);
            UUID buyerId = (UUID) purchaser.getClass().getMethod("getUniqueId").invoke(purchaser);
            UUID ownerId = (UUID) owner.getClass().getMethod("getUniqueId").invoke(owner);
            if (buyerId == null || ownerId == null || buyerId.equals(ownerId)) return;
            boolean unlimited = (boolean) shop.getClass().getMethod("isUnlimited").invoke(shop);
            if (unlimited || !isInConfiguredRegion(shop)) return;

            String path = "customers." + ownerId;
            Set<String> customers = new HashSet<>(getConfig().getStringList(path));
            if (customers.add(buyerId.toString())) {
                getConfig().set(path, customers.stream().sorted().toList());
                saveConfig();
                int count = customers.size();
                Bukkit.getLogger().info("[MerchantRole] " + ownerId + " has " + count + "/" + requiredCustomers + " distinct customers.");
                checkPlayer(ownerId);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("Could not read QuickShop-Hikari sale event: " + e.getMessage());
        }
    }

    private boolean isInConfiguredRegion(Object shop) throws ReflectiveOperationException {
        Object block = shop.getClass().getMethod("getShopBlock").invoke(shop);
        org.bukkit.Location location = ((org.bukkit.block.Block) block).getLocation();
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().stream()
                .anyMatch(r -> r.getId().equalsIgnoreCase(region));
    }

    private void checkOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) checkPlayer(player.getUniqueId());
    }

    private void checkPlayer(UUID id) {
        if (completed.contains(id)) return;
        if (getConfig().getStringList("customers." + id).size() < requiredCustomers) return;
        Player player = Bukkit.getPlayer(id);
        if (player == null || countEmeraldBlocks(player) < requiredEmeraldBlocks) return;
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + id + " parent add " + group);
        if (dispatched) {
            completed.add(id);
            getConfig().set("completed", completed.stream().map(UUID::toString).sorted().toList());
            saveConfig();
            player.sendMessage("§aMerchant I complete! You have been granted the " + group + " role.");
        } else {
            getLogger().warning("LuckPerms did not accept role grant for " + id + "; verify the group exists.");
        }
    }

    private int countEmeraldBlocks(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && item.getType() == Material.EMERALD_BLOCK) count += item.getAmount();
        return count;
    }

    private void loadCompleted() {
        for (String value : getConfig().getStringList("completed")) {
            try { completed.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        int customers = getConfig().getStringList("customers." + player.getUniqueId()).size();
        player.sendMessage("§6Merchant I: §e" + customers + "/" + requiredCustomers + " distinct customers§6; §e"
                + countEmeraldBlocks(player) + "/" + requiredEmeraldBlocks + " emerald blocks§6.");
        return true;
    }
}