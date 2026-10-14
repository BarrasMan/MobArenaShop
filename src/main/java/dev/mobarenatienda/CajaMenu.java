package dev.mobarenatienda;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Roulette shown while a mystery box opens: prizes scroll through the middle row,
 * slow down and stop with the winner under the markers.
 */
final class CajaMenu implements MenuClickable {

    private static final int SIZE = 27;
    private static final int FIRST_REEL_SLOT = 9;
    private static final int REEL_WIDTH = 9;
    private static final int CENTER_OFFSET = 4;
    private static final int STEPS = 30;
    private static final int[] MARKER_SLOTS = {4, 22};

    private final MobArenaTienda plugin;
    private final CajaMisteriosa caja;
    private final Player player;
    private final CajaMisteriosa.Premio prize;
    private final Inventory inventory;
    private final List<ItemStack> reel = new ArrayList<>();
    private int position;
    private boolean done;
    private BukkitTask task;

    CajaMenu(MobArenaTienda plugin, CajaMisteriosa caja, Player player, CajaMisteriosa.Premio prize) {
        this.plugin = plugin;
        this.caja = caja;
        this.player = player;
        this.prize = prize;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.text("caja-misteriosa.titulo"));

        // Random prizes, with the winner where the reel stops.
        for (int i = 0; i < STEPS + REEL_WIDTH; i++) {
            reel.add(i == STEPS + CENTER_OFFSET ? prize.item() : caja.randomPrize().item());
        }
        ItemStack glass = pane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, glass);
        }
        ItemStack marker = pane(Material.LIME_STAINED_GLASS_PANE);
        for (int slot : MARKER_SLOTS) {
            inventory.setItem(slot, marker);
        }
        drawReel();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    boolean isDone() {
        return done;
    }

    void start() {
        scheduleStep();
    }

    private void scheduleStep() {
        task = Bukkit.getScheduler().runTaskLater(plugin, this::step, delayFor(position));
    }

    /** Fast at first, slowing down towards the end. */
    private static long delayFor(int step) {
        if (step < 15) {
            return 1;
        }
        if (step < 22) {
            return 2;
        }
        if (step < 26) {
            return 3;
        }
        return step < 29 ? 5 : 8;
    }

    private void step() {
        if (done) {
            return;
        }
        position++;
        drawReel();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
        if (position >= STEPS) {
            complete();
        } else {
            scheduleStep();
        }
    }

    private void drawReel() {
        for (int i = 0; i < REEL_WIDTH; i++) {
            inventory.setItem(FIRST_REEL_SLOT + i, reel.get(position + i));
        }
    }

    private void complete() {
        done = true;
        caja.finish(player, this, prize);
        // Show the prize for a moment, then back to the shop.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                plugin.openShop(player);
            }
        }, 40L);
    }

    /** The player closed the roulette: stop spinning and give the prize now. */
    void skip() {
        if (done) {
            return;
        }
        done = true;
        if (task != null) {
            task.cancel();
        }
        caja.finish(player, this, prize);
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    @Override
    public void handleClick(int slot) {
        // Read-only while spinning.
    }
}
