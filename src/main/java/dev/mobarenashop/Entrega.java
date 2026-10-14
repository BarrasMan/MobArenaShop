package dev.mobarenashop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Hands bought or won items to the player. */
final class Entrega {

    private Entrega() {
    }

    /** The armor slot for this item if it is armor and that slot is empty, otherwise null. */
    static EquipmentSlot emptyArmorSlotFor(Player player, ItemStack item) {
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        boolean armor = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
        if (!armor) {
            return null;
        }
        ItemStack current = player.getInventory().getItem(slot);
        return current == null || current.getType().isAir() ? slot : null;
    }

    /** Armor goes straight to its slot when that slot is empty; anything else to the inventory. */
    static void give(Player player, ItemStack item) {
        EquipmentSlot armorSlot = emptyArmorSlotFor(player, item);
        if (armorSlot != null) {
            player.getInventory().setItem(armorSlot, item);
        } else {
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }
}
