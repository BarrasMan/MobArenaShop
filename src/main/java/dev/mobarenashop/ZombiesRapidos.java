package dev.mobarenashop;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

/**
 * Zombies get faster as the match goes on: every N waves, X% more movement speed.
 * The speed is fixed when the zombie appears, based on the wave it belongs to.
 */
final class ZombiesRapidos {

    private static final long CHECK_TICKS = 10L;

    private final MobArenaShop plugin;
    private final NamespacedKey modifierKey;
    private BukkitTask task;

    ZombiesRapidos(MobArenaShop plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "velocidad-oleada");
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Gives the bonus to every arena zombie that doesn't have it yet. */
    private void check() {
        if (!plugin.getConfig().getBoolean("zombies-rapidos.activado", true)) {
            return;
        }
        int everyWaves = Math.max(1, plugin.getConfig().getInt("zombies-rapidos.cada-oleadas", 5));
        double percent = plugin.getConfig().getDouble("zombies-rapidos.porcentaje", 5);
        Set<EntityType> types = configuredTypes();

        for (Arena arena : plugin.mobArena().getArenaMaster().getArenas()) {
            if (!arena.isRunning()) {
                continue;
            }
            int steps = arena.getWaveManager().getWaveNumber() / everyWaves;
            if (steps <= 0) {
                continue;
            }
            double bonus = steps * percent / 100.0;
            for (LivingEntity monster : arena.getMonsterManager().getMonsters()) {
                if (types.contains(monster.getType()) && monster.isValid()) {
                    speedUp(monster, bonus);
                }
            }
        }
    }

    private void speedUp(LivingEntity zombie, double bonus) {
        AttributeInstance speed = zombie.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(modifierKey) != null) {
            return;
        }
        // ADD_SCALAR: speed = base * (1 + bonus). Transient: never saved with the mob.
        speed.addTransientModifier(new AttributeModifier(modifierKey, bonus, AttributeModifier.Operation.ADD_SCALAR));
    }

    private Set<EntityType> configuredTypes() {
        Set<EntityType> types = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("zombies-rapidos.tipos")) {
            try {
                types.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown names are skipped; the rest still work.
            }
        }
        return types;
    }
}
