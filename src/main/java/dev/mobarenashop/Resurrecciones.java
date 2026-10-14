package dev.mobarenashop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.garbagemule.MobArena.events.ArenaEndEvent;
import com.garbagemule.MobArena.events.ArenaPlayerDeathEvent;
import com.garbagemule.MobArena.events.ArenaPlayerLeaveEvent;
import com.garbagemule.MobArena.events.NewWaveEvent;
import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Lets a living player pay to bring a fallen teammate back. The teammate returns when
 * the next wave starts, only if the payer is still alive by then.
 */
final class Resurrecciones implements Listener {

    private final MobArenaShop plugin;
    /** Players who died in a match -> arena config name. */
    private final Map<UUID, String> fallen = new HashMap<>();
    /** Fallen player -> player who paid for the revive. */
    private final Map<UUID, UUID> pending = new HashMap<>();

    Resurrecciones(MobArenaShop plugin) {
        this.plugin = plugin;
    }

    int price() {
        return plugin.getConfig().getInt("revivir.precio", 50);
    }

    /** Teammates of this arena that died and are still watching as spectators. */
    List<Player> fallenIn(Arena arena) {
        List<Player> result = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : fallen.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && entry.getValue().equals(arena.configName()) && arena.inSpec(player)) {
                result.add(player);
            }
        }
        return result;
    }

    boolean isPending(Player target) {
        return pending.containsKey(target.getUniqueId());
    }

    void pay(Player payer, Player target) {
        Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(payer);
        Map<String, String> names = Map.of("player", target.getName(), "payer", payer.getName());
        if (arena == null || !arena.inArena(payer)) {
            payer.sendMessage(plugin.text("mensajes.solo-en-partida"));
            return;
        }
        if (!fallenIn(arena).contains(target)) {
            payer.sendMessage(plugin.text("mensajes.revivir-no-disponible", names));
            return;
        }
        if (isPending(target)) {
            payer.sendMessage(plugin.text("mensajes.revivir-ya-pagado", names));
            return;
        }
        int coins = Monedero.count(payer, plugin.coin());
        if (coins < price()) {
            payer.sendMessage(plugin.text("mensajes.sin-monedas", Map.of("missing", String.valueOf(price() - coins))));
            payer.playSound(payer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Monedero.take(payer, plugin.coin(), price());
        pending.put(target.getUniqueId(), payer.getUniqueId());
        payer.sendMessage(plugin.text("mensajes.revivir-pagado", names));
        target.sendMessage(plugin.text("mensajes.revivir-aviso", names));
        payer.playSound(payer.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(ArenaPlayerDeathEvent event) {
        Player dead = event.getPlayer();
        fallen.put(dead.getUniqueId(), event.getArena().configName());
        cancelPaidBy(dead);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeave(ArenaPlayerLeaveEvent event) {
        Player player = event.getPlayer();
        fallen.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
        cancelPaidBy(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArenaEnd(ArenaEndEvent event) {
        String arenaName = event.getArena().configName();
        Iterator<Map.Entry<UUID, String>> it = fallen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            if (entry.getValue().equals(arenaName)) {
                pending.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /** A new wave means the previous one was survived: bring paid teammates back. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewWave(NewWaveEvent event) {
        Arena arena = event.getArena();
        Iterator<Map.Entry<UUID, UUID>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            if (!arena.configName().equals(fallen.get(entry.getKey()))) {
                continue;
            }
            it.remove();
            Player target = Bukkit.getPlayer(entry.getKey());
            Player payer = Bukkit.getPlayer(entry.getValue());
            if (target == null || !arena.inSpec(target) || payer == null || !arena.inArena(payer)) {
                continue;
            }
            Map<String, String> names = Map.of("player", target.getName(), "payer", payer.getName());
            if (ArenaInternals.bringBack(plugin, arena, target, payer.getLocation())) {
                fallen.remove(target.getUniqueId());
                target.sendMessage(plugin.text("mensajes.revivir-vuelve", names));
                for (Player teammate : arena.getPlayersInArena()) {
                    if (teammate != target) {
                        teammate.sendMessage(plugin.text("mensajes.revivir-hecho", names));
                    }
                }
                target.playSound(target.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1f);
            } else {
                payer.sendMessage(plugin.text("mensajes.revivir-error", names));
            }
        }
    }

    /** The payer died or left before the wave ended: the revive is lost. */
    private void cancelPaidBy(Player payer) {
        Iterator<Map.Entry<UUID, UUID>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            if (!entry.getValue().equals(payer.getUniqueId())) {
                continue;
            }
            it.remove();
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                Map<String, String> names = Map.of("player", target.getName(), "payer", payer.getName());
                target.sendMessage(plugin.text("mensajes.revivir-fallido", names));
                payer.sendMessage(plugin.text("mensajes.revivir-fallido", names));
            }
        }
    }
}
