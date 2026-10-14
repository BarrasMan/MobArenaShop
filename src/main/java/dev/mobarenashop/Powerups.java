package dev.mobarenashop;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.garbagemule.MobArena.events.ArenaKillEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * Power-ups dropped by arena mobs. Each power-up is an item on the ground that
 * activates when a playing player picks it up.
 */
final class Powerups implements Listener {

    static final String LANZANIEVES = "lanzanieves";
    static final String VIDA = "vida";
    static final String BOOOM = "booom";
    static final String VELOCIDAD = "velocidad";
    private static final List<String> TYPES = List.of(LANZANIEVES, VIDA, BOOOM, VELOCIDAD);

    private final MobArenaShop plugin;
    private final NamespacedKey powerupKey;
    private final NamespacedKey uniqueKey;
    private final Lanzanieves lanzanieves;
    private final Booom booom;
    private final Velocidad velocidad;

    Powerups(MobArenaShop plugin) {
        this.plugin = plugin;
        this.powerupKey = new NamespacedKey(plugin, "powerup");
        this.uniqueKey = new NamespacedKey(plugin, "powerup-id");
        this.lanzanieves = new Lanzanieves(plugin);
        this.booom = new Booom(plugin);
        this.velocidad = new Velocidad(plugin);
    }

    Lanzanieves lanzanieves() {
        return lanzanieves;
    }

    Booom booom() {
        return booom;
    }

    Velocidad velocidad() {
        return velocidad;
    }

    @EventHandler
    public void onArenaKill(ArenaKillEvent event) {
        // Kills from a BOOOM explosion don't drop more power-ups (they would chain forever).
        if (booom.isExploding()) {
            return;
        }
        double chance = plugin.getConfig().getDouble("powerups.probabilidad", 2.5);
        if (ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        String type = pickType();
        if (type != null) {
            drop(event.getVictim().getLocation(), type);
        }
    }

    /** Random enabled power-up, weighted by its "peso". */
    private String pickType() {
        double total = 0;
        for (String type : TYPES) {
            total += weight(type);
        }
        if (total <= 0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (String type : TYPES) {
            roll -= weight(type);
            if (roll < 0) {
                return type;
            }
        }
        return null;
    }

    private double weight(String type) {
        String path = "powerups." + type + ".";
        return plugin.getConfig().getBoolean(path + "activado", true)
                ? Math.max(0, plugin.getConfig().getDouble(path + "peso", 1)) : 0;
    }

    private void drop(Location location, String type) {
        ItemStack orb = orbItem(type);
        ItemMeta meta = orb.getItemMeta();
        Component name = plugin.text("powerups." + type + ".nombre");
        meta.displayName(name);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(powerupKey, PersistentDataType.STRING, type);
        // Unique id so two power-ups on the ground never merge into one stack.
        meta.getPersistentDataContainer().set(uniqueKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        orb.setItemMeta(meta);

        Item item = location.getWorld().dropItemNaturally(location, orb);
        item.customName(name);
        item.setCustomNameVisible(true);
        item.setGlowing(true);
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);

        long lifetime = plugin.getConfig().getLong("powerups.segundos-en-suelo", 15) * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid()) {
                item.remove();
            }
        }, lifetime);
    }

    private static ItemStack orbItem(String type) {
        return switch (type) {
            case VIDA -> {
                ItemStack potion = new ItemStack(Material.POTION);
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                meta.setBasePotionType(PotionType.HEALING);
                potion.setItemMeta(meta);
                yield potion;
            }
            case BOOOM -> new ItemStack(Material.TNT);
            case VELOCIDAD -> new ItemStack(Material.GOLDEN_BOOTS);
            default -> new ItemStack(Material.SNOWBALL);
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemMeta meta = event.getItem().getItemStack().getItemMeta();
        String type = meta == null ? null : meta.getPersistentDataContainer().get(powerupKey, PersistentDataType.STRING);
        if (type == null) {
            return;
        }
        // Mobs and people outside the fight never take power-ups.
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player) || !plugin.isPlaying(player)) {
            return;
        }
        if (activate(player, type)) {
            event.getItem().remove();
        }
    }

    /** Returns false if the power-up could not be taken and must stay on the ground. */
    private boolean activate(Player player, String type) {
        switch (type) {
            case LANZANIEVES -> {
                return lanzanieves.activate(player);
            }
            case VIDA -> heal(player);
            case BOOOM -> booom.launch(player);
            case VELOCIDAD -> velocidad.activate(player);
            default -> {
                return true;
            }
        }
        return true;
    }

    private void heal(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2f);
        player.sendMessage(plugin.text("mensajes.powerup-vida"));
    }

    void shutdown() {
        lanzanieves.endAll();
        velocidad.endAll();
    }
}
