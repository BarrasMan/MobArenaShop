package dev.mobarenatienda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.garbagemule.MobArena.framework.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Mystery box sold in the shop. It is opened right away when bought (it never exists
 * as an item): a roulette spins through the possible prizes and stops on the winner.
 */
final class CajaMisteriosa implements Listener {

    record Premio(String id, ItemStack item, double weight, boolean announce) {
    }

    private final MobArenaTienda plugin;
    private final List<Premio> prizes = new ArrayList<>();
    private final Map<UUID, CajaMenu> opening = new HashMap<>();

    CajaMisteriosa(MobArenaTienda plugin) {
        this.plugin = plugin;
    }

    void load() {
        prizes.clear();
        ConfigurationSection list = plugin.getConfig().getConfigurationSection("caja-misteriosa.premios");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection section = list.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                try {
                    ItemStack item = Tienda.createItem(section);
                    String rarity = section.getString("rareza");
                    if (rarity != null) {
                        ItemMeta meta = item.getItemMeta();
                        meta.lore(List.of(MobArenaTienda.colorize(rarity)));
                        item.setItemMeta(meta);
                    }
                    double weight = section.getDouble("peso", 1);
                    if (weight <= 0) {
                        throw new IllegalArgumentException("'peso' (weight) must be greater than 0");
                    }
                    prizes.add(new Premio(id, item, weight, section.getBoolean("anunciar", false)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Mystery box prize '" + id + "' ignored: " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Mystery box loaded with " + prizes.size() + " prizes.");
    }

    int price() {
        return plugin.getConfig().getInt("caja-misteriosa.precio", 100);
    }

    boolean hasPrizes() {
        return !prizes.isEmpty();
    }

    /** The box as shown in the shop. */
    ItemStack icon(int coins) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("caja-misteriosa.material", "ENDER_CHEST"));
        ItemStack icon = new ItemStack(material == null ? Material.ENDER_CHEST : material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(plugin.text("caja-misteriosa.nombre"));
        meta.setEnchantmentGlintOverride(true);
        List<Component> lore = new ArrayList<>(plugin.textList("caja-misteriosa.lore", Map.of()));
        lore.add(Component.empty());
        lore.add(plugin.text("gui.precio", Map.of("price", String.valueOf(price()))));
        lore.add(coins >= price()
                ? plugin.text("gui.puedes-comprar")
                : plugin.text("gui.te-faltan", Map.of("missing", String.valueOf(price() - coins))));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    Premio randomPrize() {
        double total = prizes.stream().mapToDouble(Premio::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (Premio prize : prizes) {
            roll -= prize.weight();
            if (roll < 0) {
                return prize;
            }
        }
        return prizes.get(prizes.size() - 1);
    }

    /** Called from the shop. Pays, picks the prize and starts the roulette. */
    void buy(Player player) {
        if (!plugin.isPlaying(player)) {
            player.sendMessage(plugin.text("mensajes.solo-en-partida"));
            return;
        }
        if (!hasPrizes() || opening.containsKey(player.getUniqueId())) {
            return;
        }
        int coins = Monedero.count(player, plugin.coin());
        if (coins < price()) {
            fail(player, plugin.text("mensajes.sin-monedas", Map.of("missing", String.valueOf(price() - coins))));
            return;
        }
        // Prizes don't stack: there must be a free slot once the box is paid.
        if (!Monedero.fitsAfterPaying(player, plugin.coin(), price(), new ItemStack(Material.DIAMOND_SWORD))) {
            fail(player, plugin.text("mensajes.inventario-lleno"));
            return;
        }

        Monedero.take(player, plugin.coin(), price());
        Premio prize = randomPrize();
        if (!plugin.getConfig().getBoolean("caja-misteriosa.animacion", true)) {
            deliver(player, prize);
            return;
        }
        CajaMenu menu = new CajaMenu(plugin, this, player, prize);
        opening.put(player.getUniqueId(), menu);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.openInventory(menu.getInventory());
            menu.start();
        });
    }

    /** End of the roulette (or of a closed roulette): hand over the prize. */
    void finish(Player player, CajaMenu menu, Premio prize) {
        if (opening.get(player.getUniqueId()) != menu) {
            return;
        }
        opening.remove(player.getUniqueId());
        deliver(player, prize);
    }

    private void deliver(Player player, Premio prize) {
        // Never hand an arena prize to someone who already left the fight.
        if (!player.isOnline() || !plugin.isPlaying(player)) {
            if (player.isOnline()) {
                player.sendMessage(plugin.text("mensajes.caja-perdida"));
            }
            return;
        }
        Entrega.give(player, prize.item().clone());
        String name = PlainTextComponentSerializer.plainText().serialize(prize.item().effectiveName());
        Map<String, String> placeholders = Map.of("player", player.getName(), "item", name);
        player.sendMessage(plugin.text("mensajes.caja-premio", placeholders));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (prize.announce()) {
            Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(player);
            if (arena != null) {
                for (Player teammate : arena.getPlayersInArena()) {
                    teammate.sendMessage(plugin.text("mensajes.caja-anuncio", placeholders));
                    teammate.playSound(teammate.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
                }
            }
        }
    }

    private void fail(Player player, Component message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /** Closing the roulette early skips the animation but still gives the prize. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof CajaMenu menu && !menu.isDone()) {
            menu.skip();
        }
    }
}
