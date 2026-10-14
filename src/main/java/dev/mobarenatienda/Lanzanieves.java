package dev.mobarenatienda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.garbagemule.MobArena.MonsterManager;
import com.garbagemule.MobArena.events.ArenaPlayerDeathEvent;
import com.garbagemule.MobArena.events.ArenaPlayerLeaveEvent;
import com.garbagemule.MobArena.framework.Arena;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Power-up: a hoe that shoots unlimited damaging snowballs for a limited time. */
final class Lanzanieves implements Listener {

    private static final String CONFIG = "powerups." + Powerups.LANZANIEVES + ".";

    private final MobArenaTienda plugin;
    private final NamespacedKey weaponKey;
    private final Map<UUID, Active> active = new HashMap<>();

    private static final class Active {
        int secondsLeft;
        long lastShot;
        BossBar bar;
        BukkitTask timer;
    }

    Lanzanieves(MobArenaTienda plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "lanzanieves");
    }

    private int duration() {
        return Math.max(1, plugin.getConfig().getInt(CONFIG + "duracion", 30));
    }

    /** Returns false (and leaves the power-up on the ground) if the hoe does not fit. */
    boolean activate(Player player) {
        Active current = active.get(player.getUniqueId());
        if (current != null) {
            current.secondsLeft = duration();
            player.sendMessage(plugin.text("mensajes.lanzanieves-ampliado",
                    Map.of("seconds", String.valueOf(duration()))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            updateBar(current);
            return true;
        }

        if (!player.getInventory().addItem(createWeapon()).isEmpty()) {
            player.sendActionBar(plugin.text("mensajes.powerup-sin-espacio"));
            return false;
        }

        Active state = new Active();
        state.secondsLeft = duration();
        state.bar = BossBar.bossBar(plugin.text("gui.lanzanieves-barra"), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        updateBar(state);
        player.showBossBar(state.bar);
        state.timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player), 20L, 20L);
        active.put(player.getUniqueId(), state);

        player.sendMessage(plugin.text("mensajes.lanzanieves-activado", Map.of("seconds", String.valueOf(duration()))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        return true;
    }

    private ItemStack createWeapon() {
        Material material = Material.matchMaterial(plugin.getConfig().getString(CONFIG + "material-arma", "DIAMOND_HOE"));
        ItemStack weapon = new ItemStack(material == null ? Material.DIAMOND_HOE : material);
        ItemMeta meta = weapon.getItemMeta();
        meta.displayName(plugin.text("gui.lanzanieves-arma"));
        meta.lore(plugin.textList("gui.lanzanieves-arma-lore", Map.of()));
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.BYTE, (byte) 1);
        weapon.setItemMeta(meta);
        return weapon;
    }

    private boolean isWeapon(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(weaponKey, PersistentDataType.BYTE);
    }

    private void tick(Player player) {
        Active state = active.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.secondsLeft--;
        if (state.secondsLeft <= 0 || !player.isOnline() || !plugin.isPlaying(player)) {
            end(player, true);
            return;
        }
        updateBar(state);
    }

    private void updateBar(Active state) {
        state.bar.name(plugin.text("gui.lanzanieves-barra", Map.of("seconds", String.valueOf(state.secondsLeft))));
        state.bar.progress(Math.max(0f, Math.min(1f, state.secondsLeft / (float) duration())));
    }

    private void end(Player player, boolean notify) {
        Active state = active.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.timer.cancel();
        player.hideBossBar(state.bar);
        removeWeapons(player);
        if (notify && player.isOnline()) {
            player.sendMessage(plugin.text("mensajes.lanzanieves-fin"));
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

    private void removeWeapons(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWeapon(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isWeapon(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    // Not ignoreCancelled: right-clicking air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onShoot(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWeapon(event.getItem())
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Active state = active.get(player.getUniqueId());
        if (state == null) {
            removeWeapons(player);
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong(CONFIG + "cadencia-ticks", 4) * 50L;
        if (now - state.lastShot < cooldownMs) {
            return;
        }
        state.lastShot = now;

        double speed = plugin.getConfig().getDouble(CONFIG + "velocidad", 1.8);
        Snowball ball = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(speed));
        ball.getPersistentDataContainer().set(weaponKey, PersistentDataType.BYTE, (byte) 1);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.5f, 1.4f);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)
                || !ball.getPersistentDataContainer().has(weaponKey, PersistentDataType.BYTE)
                || !(ball.getShooter() instanceof Player shooter)
                || !(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(shooter);
        if (arena == null) {
            return;
        }
        // Only arena monsters: never teammates, pets or golems.
        MonsterManager monsters = arena.getMonsterManager();
        if (!monsters.getMonsters().contains(target) && !monsters.getBossMonsters().contains(target)) {
            return;
        }
        // Rapid fire would otherwise be swallowed by the target's hit invulnerability.
        target.setNoDamageTicks(0);
        // Damage attributed to the shooter, so the kill counts (and pays coins) as theirs.
        target.damage(plugin.getConfig().getDouble(CONFIG + "danio", 5.0), shooter);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isWeapon(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
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
