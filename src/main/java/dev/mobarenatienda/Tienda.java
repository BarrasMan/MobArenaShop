package dev.mobarenatienda;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Shop layout and articles read from config.yml. */
record Tienda(int rows, int infoSlot, int reviveSlot, int boxSlot, Material filler, Map<Integer, Articulo> articles) {

    record Articulo(String id, ItemStack item, int price) {
    }

    static Tienda load(MobArenaTienda plugin) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("tienda");
        if (config == null) {
            config = plugin.getConfig().createSection("tienda");
        }
        int rows = Math.max(1, Math.min(6, config.getInt("filas", 5)));
        int size = rows * 9;
        int infoSlot = config.getInt("slot-info", 40);
        if (infoSlot >= size) {
            infoSlot = -1;
        }
        int reviveSlot = plugin.getConfig().getBoolean("revivir.activado", true)
                ? plugin.getConfig().getInt("revivir.slot", 4) : -1;
        if (reviveSlot >= size || reviveSlot == infoSlot) {
            plugin.getLogger().warning("revivir.slot is outside the menu or the same as slot-info; revive button hidden.");
            reviveSlot = -1;
        }
        int boxSlot = plugin.getConfig().getBoolean("caja-misteriosa.activado", true)
                ? plugin.getConfig().getInt("caja-misteriosa.slot", 41) : -1;
        if (boxSlot >= size || boxSlot == infoSlot || boxSlot == reviveSlot) {
            plugin.getLogger().warning("caja-misteriosa.slot is outside the menu or used by another button; mystery box hidden.");
            boxSlot = -1;
        }
        Material filler = Material.matchMaterial(config.getString("relleno", "AIR"));
        if (filler == null || !filler.isItem()) {
            filler = Material.AIR;
        }

        Map<Integer, Articulo> articles = new HashMap<>();
        ConfigurationSection list = config.getConfigurationSection("articulos");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection section = list.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                try {
                    int slot = section.getInt("slot", -1);
                    if (slot < 0 || slot >= size || slot == infoSlot || slot == reviveSlot || slot == boxSlot) {
                        throw new IllegalArgumentException(
                                "slot " + slot + " is outside the menu or used by the info, revive or mystery box button");
                    }
                    if (articles.containsKey(slot)) {
                        throw new IllegalArgumentException("slot " + slot + " is already used by '" + articles.get(slot).id() + "'");
                    }
                    int price = section.getInt("precio", -1);
                    if (price < 0) {
                        throw new IllegalArgumentException("missing 'precio' (price)");
                    }
                    articles.put(slot, new Articulo(id, createItem(section), price));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Shop article '" + id + "' ignored: " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Shop loaded with " + articles.size() + " articles.");
        return new Tienda(rows, infoSlot, reviveSlot, boxSlot, filler, articles);
    }

    /** Builds an item from material / cantidad / encantamientos / pocion / nombre. */
    static ItemStack createItem(ConfigurationSection section) {
        String materialName = section.getString("material", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("unknown material '" + materialName + "'");
        }
        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("cantidad", 1)));
        ItemMeta meta = item.getItemMeta();

        ConfigurationSection enchantments = section.getConfigurationSection("encantamientos");
        if (enchantments != null) {
            for (String name : enchantments.getKeys(false)) {
                Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                        .get(Key.key(name.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    throw new IllegalArgumentException("unknown enchantment '" + name + "'");
                }
                meta.addEnchant(enchantment, enchantments.getInt(name, 1), true);
            }
        }

        String potion = section.getString("pocion");
        if (potion != null) {
            if (!(meta instanceof PotionMeta potionMeta)) {
                throw new IllegalArgumentException("'pocion' only works for potions and tipped arrows");
            }
            try {
                potionMeta.setBasePotionType(PotionType.valueOf(potion.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown potion '" + potion + "'");
            }
        }

        String name = section.getString("nombre");
        if (name != null) {
            meta.displayName(MobArenaTienda.colorize(name));
        }
        item.setItemMeta(meta);
        return item;
    }
}
