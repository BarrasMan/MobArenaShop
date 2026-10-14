package dev.mobarenatienda;

import java.util.Arrays;

import com.garbagemule.MobArena.commands.Command;
import com.garbagemule.MobArena.commands.CommandInfo;
import com.garbagemule.MobArena.framework.ArenaMaster;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "shop",
        pattern = "shop|tienda",
        usage = "/ma shop",
        desc = "open the arena shop",
        permission = "mobarenatienda.use")
final class TiendaCommand implements Command {

    private final MobArenaTienda plugin;

    TiendaCommand(MobArenaTienda plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ArenaMaster am, CommandSender sender, String... args) {
        if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("recargar") || arg.equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission("mobarenatienda.admin")) {
                sender.sendMessage(plugin.text("mensajes.sin-permiso"));
                return true;
            }
            plugin.loadSettings();
            sender.sendMessage(plugin.text("mensajes.recargado"));
            return true;
        }
        if (sender instanceof Player player) {
            plugin.openShop(player);
        }
        return true;
    }
}
