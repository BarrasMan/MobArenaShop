package dev.mobarenashop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.garbagemule.MobArena.framework.Arena;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Boss bar showing how many monsters are left in the current wave, for the players
 * and spectators of each running arena.
 */
final class ContadorMobs {

    private static final long UPDATE_TICKS = 5L;

    private static final class State {
        final BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
        int wave = -1;
        /** Most monsters seen alive at once during this wave: the wave's total. */
        int total;

        State(BossBar bar) {
            this.bar = bar;
        }
    }

    private final MobArenaShop plugin;
    private final Map<String, State> states = new HashMap<>();
    private BukkitTask task;

    ContadorMobs(MobArenaShop plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, UPDATE_TICKS, UPDATE_TICKS);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        for (State state : states.values()) {
            hideAll(state);
        }
        states.clear();
    }

    private void update() {
        boolean enabled = plugin.getConfig().getBoolean("contador-mobs.activado", true);
        Set<String> running = new HashSet<>();
        if (enabled) {
            for (Arena arena : plugin.mobArena().getArenaMaster().getArenas()) {
                if (arena.isRunning()) {
                    running.add(arena.configName());
                    updateArena(arena);
                }
            }
        }
        // Arenas that ended (or the counter was disabled): remove their bars.
        Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, State> entry = it.next();
            if (!running.contains(entry.getKey())) {
                hideAll(entry.getValue());
                it.remove();
            }
        }
    }

    private void updateArena(Arena arena) {
        State state = states.computeIfAbsent(arena.configName(),
                name -> new State(BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)));

        int alive = 0;
        for (LivingEntity monster : arena.getMonsterManager().getMonsters()) {
            if (monster.isValid() && !monster.isDead()) {
                alive++;
            }
        }
        int wave = arena.getWaveManager().getWaveNumber();
        if (wave != state.wave) {
            state.wave = wave;
            state.total = alive;
        } else {
            state.total = Math.max(state.total, alive);
        }

        Map<String, String> placeholders = Map.of(
                "wave", String.valueOf(wave),
                "remaining", String.valueOf(alive),
                "killed", String.valueOf(state.total - alive),
                "total", String.valueOf(state.total));
        boolean cleared = alive == 0;
        state.bar.name(plugin.text(cleared ? "gui.contador-mobs-despejada" : "gui.contador-mobs", placeholders));
        state.bar.color(cleared ? BossBar.Color.GREEN : BossBar.Color.RED);
        state.bar.progress(state.total > 0 ? Math.min(1f, alive / (float) state.total) : 0f);

        Set<UUID> audience = new HashSet<>();
        for (Player player : arena.getPlayersInArena()) {
            audience.add(player.getUniqueId());
        }
        for (Player player : arena.getSpectators()) {
            audience.add(player.getUniqueId());
        }
        for (UUID id : audience) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && state.viewers.add(id)) {
                player.showBossBar(state.bar);
            }
        }
        Iterator<UUID> it = state.viewers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!audience.contains(id)) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) {
                    player.hideBossBar(state.bar);
                }
                it.remove();
            }
        }
    }

    private void hideAll(State state) {
        for (UUID id : state.viewers) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.hideBossBar(state.bar);
            }
        }
        state.viewers.clear();
    }
}
