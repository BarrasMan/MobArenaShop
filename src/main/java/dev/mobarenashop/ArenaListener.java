package dev.mobarenashop;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.garbagemule.MobArena.events.ArenaKillEvent;
import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class ArenaListener implements Listener {

    private final MobArenaShop plugin;
    /** Bosses that just took a lethal hit; MobArena unregisters them before ArenaKillEvent. */
    private final Set<UUID> dyingBosses = new HashSet<>();

    ArenaListener(MobArenaShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || event.getFinalDamage() < entity.getHealth()) {
            return;
        }
        for (Arena arena : plugin.mobArena().getArenaMaster().getArenas()) {
            if (arena.getMonsterManager().getBossMonsters().contains(entity)) {
                UUID id = entity.getUniqueId();
                dyingBosses.add(id);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> dyingBosses.remove(id), 20L);
                return;
            }
        }
    }

    @EventHandler
    public void onArenaKill(ArenaKillEvent event) {
        Player player = event.getPlayer();
        if (player == null || !event.getArena().inArena(player)) {
            return;
        }
        boolean boss = dyingBosses.remove(event.getVictim().getUniqueId());
        int amount = plugin.getConfig().getInt(boss ? "moneda.por-jefe" : "moneda.por-mob", boss ? 10 : 1);
        if (amount <= 0) {
            return;
        }
        Monedero.give(player, plugin.coin(), amount);
        int total = Monedero.count(player, plugin.coin());
        player.sendActionBar(plugin.text("mensajes.moneda-ganada", Map.of(
                "amount", String.valueOf(amount),
                "total", String.valueOf(total))));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickCoin(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !plugin.getConfig().getBoolean("abrir-con-moneda", true)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != plugin.coin() || !plugin.isPlaying(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        plugin.openShop(event.getPlayer());
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MenuClickable menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == top && event.getClick().isLeftClick()) {
            menu.handleClick(event.getRawSlot());
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuClickable) {
            event.setCancelled(true);
        }
    }
}
