package dev.mobarenatienda;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The arena shop menu. One instance per open menu. */
final class TiendaMenu implements MenuClickable {

    private final MobArenaTienda plugin;
    private final Player player;
    private final Tienda tienda;
    private final Inventory inventory;

    TiendaMenu(MobArenaTienda plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.tienda = plugin.tienda();
        this.inventory = Bukkit.createInventory(this, tienda.rows() * 9, plugin.text("tienda.titulo"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void render() {
        int coins = Monedero.count(player, plugin.coin());
        inventory.clear();

        for (Map.Entry<Integer, Tienda.Articulo> entry : tienda.articles().entrySet()) {
            inventory.setItem(entry.getKey(), icon(entry.getValue(), coins));
        }

        if (tienda.infoSlot() >= 0) {
            ItemStack info = new ItemStack(plugin.coin(), Math.max(1, Math.min(coins, 64)));
            ItemMeta meta = info.getItemMeta();
            meta.displayName(plugin.text("gui.info-nombre", Map.of("total", String.valueOf(coins))));
            meta.lore(List.of(plugin.text("gui.info-lore")));
            info.setItemMeta(meta);
            inventory.setItem(tienda.infoSlot(), info);
        }

        if (tienda.reviveSlot() >= 0) {
            inventory.setItem(tienda.reviveSlot(), reviveButton());
        }

        if (tienda.boxSlot() >= 0 && plugin.caja().hasPrizes()) {
            inventory.setItem(tienda.boxSlot(), plugin.caja().icon(coins));
        }

        if (!tienda.filler().isAir()) {
            ItemStack filler = new ItemStack(tienda.filler());
            ItemMeta meta = filler.getItemMeta();
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, filler);
                }
            }
        }
    }

    private ItemStack icon(Tienda.Articulo article, int coins) {
        ItemStack icon = article.item().clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        lore.add(plugin.text("gui.precio", Map.of("price", String.valueOf(article.price()))));
        lore.add(coins >= article.price()
                ? plugin.text("gui.puedes-comprar")
                : plugin.text("gui.te-faltan", Map.of("missing", String.valueOf(article.price() - coins))));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack reviveButton() {
        Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(player);
        int fallen = arena == null ? 0 : plugin.resurrecciones().fallenIn(arena).size();
        ItemStack button = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(plugin.text("gui.revivir-nombre"));
        List<Component> lore = new ArrayList<>(plugin.textList("gui.revivir-lore",
                Map.of("price", String.valueOf(plugin.resurrecciones().price()))));
        lore.add(Component.empty());
        lore.add(fallen > 0
                ? plugin.text("gui.revivir-caidos", Map.of("count", String.valueOf(fallen)))
                : plugin.text("gui.revivir-sin-caidos"));
        meta.lore(lore);
        button.setItemMeta(meta);
        return button;
    }

    @Override
    public void handleClick(int slot) {
        if (slot == tienda.reviveSlot()) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.openReviveMenu(player));
            return;
        }
        if (slot == tienda.boxSlot()) {
            plugin.caja().buy(player);
            return;
        }
        Tienda.Articulo article = tienda.articles().get(slot);
        if (article != null) {
            buy(article);
        }
    }

    private void buy(Tienda.Articulo article) {
        Material coin = plugin.coin();
        // The player may have died or the arena may have ended while the menu was open.
        if (!plugin.isPlaying(player)) {
            player.sendMessage(plugin.text("mensajes.solo-en-partida"));
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        int coins = Monedero.count(player, coin);
        if (coins < article.price()) {
            fail(plugin.text("mensajes.sin-monedas", Map.of("missing", String.valueOf(article.price() - coins))));
            return;
        }

        ItemStack item = article.item().clone();
        if (Entrega.emptyArmorSlotFor(player, item) == null
                && !Monedero.fitsAfterPaying(player, coin, article.price(), item)) {
            fail(plugin.text("mensajes.inventario-lleno"));
            return;
        }

        Monedero.take(player, coin, article.price());
        Entrega.give(player, item);

        player.sendMessage(plugin.text("mensajes.comprado", Map.of(
                "item", itemName(item),
                "price", String.valueOf(article.price()))));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        render();
    }

    private String itemName(ItemStack item) {
        String name = PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
        return item.getAmount() > 1 ? item.getAmount() + "x " + name : name;
    }

    private void fail(Component message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }
}
