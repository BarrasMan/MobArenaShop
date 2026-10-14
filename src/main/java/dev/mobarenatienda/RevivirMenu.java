package dev.mobarenatienda;

import java.util.List;
import java.util.Map;

import com.garbagemule.MobArena.framework.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Lists fallen teammates; clicking a head pays for that teammate's revive. */
final class RevivirMenu implements MenuClickable {

    private static final int SIZE = 27;
    private static final int MAX_HEADS = 18;
    private static final int SLOT_BACK = 22;

    private final MobArenaTienda plugin;
    private final Player player;
    private final Inventory inventory;
    private final Player[] slotTargets = new Player[MAX_HEADS];

    RevivirMenu(MobArenaTienda plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.text("gui.revivir-titulo"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void render() {
        inventory.clear();
        Arena arena = plugin.mobArena().getArenaMaster().getArenaWithPlayer(player);
        List<Player> fallen = arena == null ? List.of() : plugin.resurrecciones().fallenIn(arena);
        String price = String.valueOf(plugin.resurrecciones().price());

        for (int slot = 0; slot < MAX_HEADS; slot++) {
            Player target = slot < fallen.size() ? fallen.get(slot) : null;
            slotTargets[slot] = target;
            if (target == null) {
                continue;
            }
            Map<String, String> placeholders = Map.of("player", target.getName(), "price", price);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(plugin.text("gui.revivir-jugador", placeholders));
            meta.lore(plugin.textList(plugin.resurrecciones().isPending(target)
                    ? "gui.revivir-jugador-pagado" : "gui.revivir-jugador-lore", placeholders));
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
        }

        if (fallen.isEmpty()) {
            inventory.setItem(4, named(Material.BARRIER, "gui.revivir-sin-caidos"));
        }
        inventory.setItem(SLOT_BACK, named(Material.ARROW, "gui.volver"));
    }

    private ItemStack named(Material material, String path) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.text(path));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void handleClick(int slot) {
        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.openShop(player));
            return;
        }
        if (slot >= 0 && slot < MAX_HEADS && slotTargets[slot] != null) {
            plugin.resurrecciones().pay(player, slotTargets[slot]);
            render();
        }
    }
}
