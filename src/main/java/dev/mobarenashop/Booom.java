package dev.mobarenashop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.garbagemule.MobArena.MonsterManager;
import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Power-up: throws a TNT that, instead of exploding for real, kills every monster of
 * the current wave. The real explosion is cancelled before it happens, so no block
 * breaks and no player is hurt.
 */
final class Booom implements Listener {

    private record Launch(Arena arena, UUID thrower) {
    }

    private final MobArenaShop plugin;
    private final NamespacedKey tntKey;
    private final Map<UUID, Launch> launched = new HashMap<>();
    private boolean exploding;

    Booom(MobArenaShop plugin) {
        this.plugin = plugin;
        this.tntKey = new NamespacedKey(plugin, "booom");
    }

    /** True while the wave is being killed, so those kills don't drop new power-ups. */
    boolean isExploding() {
        return exploding;
    }

    void launch(Player player) {
        Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(player);
        if (arena == null) {
            return;
        }
        int fuseTicks = (int) Math.max(1, plugin.getConfig().getDouble("powerups.booom.mecha", 2) * 20);
        Location eye = player.getEyeLocation();
        TNTPrimed tnt = eye.getWorld().spawn(eye, TNTPrimed.class, spawned -> {
            spawned.setFuseTicks(fuseTicks);
            spawned.setSource(player);
            spawned.setVelocity(eye.getDirection().multiply(0.9).add(new Vector(0, 0.25, 0)));
            spawned.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 1);
        });
        launched.put(tnt.getUniqueId(), new Launch(arena, player.getUniqueId()));
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        player.sendMessage(plugin.text("mensajes.powerup-booom"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(ExplosionPrimeEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(tntKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Launch launch = launched.remove(event.getEntity().getUniqueId());
        Location center = event.getEntity().getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, 1, 1, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.8f);
        if (launch == null || !launch.arena().isRunning()) {
            return;
        }
        killWave(launch);
    }

    private void killWave(Launch launch) {
        Arena arena = launch.arena();
        Player thrower = plugin.getServer().getPlayer(launch.thrower());
        // Kills credited to the thrower (coins) only while they are still fighting.
        Player credit = thrower != null && arena.inArena(thrower) ? thrower : null;
        boolean killBosses = plugin.getConfig().getBoolean("powerups.booom.matar-jefes", false);

        MonsterManager monsters = arena.getMonsterManager();
        List<LivingEntity> targets = new ArrayList<>(monsters.getMonsters());
        exploding = true;
        int killed = 0;
        try {
            for (LivingEntity target : targets) {
                if (!target.isValid() || target.isDead()
                        || (!killBosses && monsters.getBossMonsters().contains(target))) {
                    continue;
                }
                target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
                target.setNoDamageTicks(0);
                if (credit != null) {
                    target.damage(1_000_000, credit);
                } else {
                    target.damage(1_000_000);
                }
                // Resistance or similar could survive the hit; finish it off.
                if (!target.isDead()) {
                    target.setHealth(0);
                }
                killed++;
            }
        } finally {
            exploding = false;
        }

        String name = thrower != null ? thrower.getName() : "?";
        for (Player player : arena.getPlayersInArena()) {
            player.sendMessage(plugin.text("mensajes.powerup-booom-explosion",
                    Map.of("player", name, "count", String.valueOf(killed))));
        }
    }
}
