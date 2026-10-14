package dev.mobarenashop;

import org.bukkit.inventory.InventoryHolder;

/** A menu of this plugin: read-only, receives left clicks on its slots. */
interface MenuClickable extends InventoryHolder {

    void handleClick(int slot);
}
