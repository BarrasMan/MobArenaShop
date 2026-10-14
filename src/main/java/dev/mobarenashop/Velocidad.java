package dev.mobarenashop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.garbagemule.MobArena.events.ArenaPlayerDeathEvent;
import com.garbagemule.MobArena.events.ArenaPlayerLeaveEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Power-up: double movement speed for a while, with a boss bar countdown. */
final class Velocidad implements Listener {

    private static final String CONFIG = "powerups." + Powerups.VELOCIDAD + ".";

    private final MobArenaShop plugin;
    private final Map<UUID, Active> active = new HashMap<>();

    private static final class Active {
        int secondsLeft;
        BossBar bar;
        BukkitTask timer;
    }

    Velocidad(MobArenaShop plugin) {
        this.plugin = plugin;
    }

    private int duration() {
        return Math.max(1, plugin.getConfig().getInt(CONFIG + "duracion", 15));
    }

    void activate(Player player) {
        int seconds = duration();
        // Each Speed level adds 20% movement speed: level V (amplifier 4) doubles it.
        // A stronger effect hides a class's own speed effect until it runs out.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 4, false, true, true));
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 1.4f);
        player.sendMessage(plugin.text("mensajes.powerup-velocidad", Map.of("seconds", String.valueOf(seconds))));

        Active state = active.get(player.getUniqueId());
        if (state == null) {
            state = new Active();
            state.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            player.showBossBar(state.bar);
            state.timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player), 20L, 20L);
            active.put(player.getUniqueId(), state);
        }
        // Picking another one while active restarts the countdown.
        state.secondsLeft = seconds;
        updateBar(state);
    }

    private void tick(Player player) {
        Active state = active.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.secondsLeft--;
        if (state.secondsLeft <= 0 || !player.isOnline() || !plugin.isPlaying(player)) {
            end(player, state.secondsLeft <= 0);
            return;
        }
        updateBar(state);
    }

    private void updateBar(Active state) {
        state.bar.name(plugin.text("gui.velocidad-barra", Map.of("seconds", String.valueOf(state.secondsLeft))));
        state.bar.progress(Math.max(0f, Math.min(1f, state.secondsLeft / (float) duration())));
    }

    private void end(Player player, boolean notify) {
        Active state = active.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.timer.cancel();
        player.hideBossBar(state.bar);
        if (notify && player.isOnline()) {
            player.sendMessage(plugin.text("mensajes.velocidad-fin"));
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
        }
    }

    void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                end(player, false);
            } else {
                active.remove(id).timer.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaDeath(ArenaPlayerDeathEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArenaLeave(ArenaPlayerLeaveEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), false);
    }
}
