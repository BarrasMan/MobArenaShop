package dev.mobarenashop;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.logging.Level;

import com.garbagemule.MobArena.ArenaClass;
import com.garbagemule.MobArena.ArenaPlayer;
import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * MobArena has no API to bring a spectator back into the fight (its "revivePlayer"
 * actually turns the player into a spectator). This moves the player between
 * ArenaImpl's private player sets, written against MobArena 0.109.
 */
final class ArenaInternals {

    private ArenaInternals() {
    }

    static boolean bringBack(MobArenaShop plugin, Arena arena, Player player, Location destination) {
        try {
            Set<Player> arenaPlayers = playerSet(arena, "arenaPlayers");
            Set<Player> spectators = playerSet(arena, "specPlayers");
            Set<Player> dead = playerSet(arena, "deadPlayers");
            Set<Player> moving = playerSet(arena, "movingPlayers");

            spectators.remove(player);
            dead.remove(player);
            arenaPlayers.add(player);
            ArenaPlayer arenaPlayer = arena.getArenaPlayer(player);
            if (arenaPlayer != null) {
                arenaPlayer.setDead(false);
            }

            restore(player);
            ArenaClass arenaClass = arenaPlayer == null ? null : arenaPlayer.getArenaClass();
            if (arenaClass != null) {
                arenaClass.grantItems(player);
                arenaClass.grantPotionEffects(player);
            }

            // MobArena allows any teleport while the player is marked as moving.
            moving.add(player);
            try {
                player.teleport(destination);
            } finally {
                moving.remove(player);
            }
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not revive " + player.getName() + " (is MobArena a version other than 0.109?)", e);
            return false;
        }
    }

    private static void restore(Player player) {
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    @SuppressWarnings("unchecked")
    private static Set<Player> playerSet(Arena arena, String name) throws ReflectiveOperationException {
        Class<?> type = arena.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (Set<Player>) field.get(arena);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
