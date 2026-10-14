package dev.mobarenatienda;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Counting, paying and giving the arena coin (main inventory + off hand). */
final class Monedero {

    private Monedero() {
    }

    static int count(Player player, Material coin) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == coin) {
                total += item.getAmount();
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() == coin) {
            total += offHand.getAmount();
        }
        return total;
    }

    static void give(Player player, Material coin, int amount) {
        player.getInventory().addItem(new ItemStack(coin, amount)).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    /** Takes coins from the main inventory first, then the off hand. Caller checks the balance. */
    static void take(Player player, Material coin, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        int remaining = removeFrom(storage, coin, amount);
        inventory.setStorageContents(storage);
        if (remaining > 0) {
            ItemStack offHand = inventory.getItemInOffHand();
            if (offHand.getType() == coin) {
                offHand.setAmount(offHand.getAmount() - remaining);
                inventory.setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
            }
        }
    }

    /** Whether {@code item} fits in the main inventory once {@code price} coins are paid. */
    static boolean fitsAfterPaying(Player player, Material coin, int price, ItemStack item) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = storage[i] == null ? null : storage[i].clone();
        }
        removeFrom(storage, coin, price);
        Inventory simulation = Bukkit.createInventory(null, 36);
        simulation.setContents(storage);
        return simulation.addItem(item.clone()).isEmpty();
    }

    private static int removeFrom(ItemStack[] items, Material coin, int amount) {
        int remaining = amount;
        for (int i = 0; i < items.length && remaining > 0; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType() != coin) {
                continue;
            }
            int taken = Math.min(remaining, item.getAmount());
            remaining -= taken;
            if (taken == item.getAmount()) {
                items[i] = null;
            } else {
                item.setAmount(item.getAmount() - taken);
            }
        }
        return remaining;
    }
}
