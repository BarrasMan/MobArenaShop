package dev.mobarenatienda;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import com.garbagemule.MobArena.MobArena;
import com.garbagemule.MobArena.commands.CommandHandler;
import com.garbagemule.MobArena.framework.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArenaTienda extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String DEFAULT_LANGUAGE = "en";

    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration messageDefaults = new YamlConfiguration();

    private final ConfigFiles configFiles = new ConfigFiles(getDataFolder(), this::getResource, getLogger());
    private MobArena mobArena;
    private Tienda tienda;
    private Material coin;
    private Resurrecciones resurrecciones;
    private Powerups powerups;
    private final CajaMisteriosa caja = new CajaMisteriosa(this);
    private final ContadorMobs contador = new ContadorMobs(this);
    private final ZombiesRapidos zombiesRapidos = new ZombiesRapidos(this);

    @Override
    public void onEnable() {
        mobArena = (MobArena) getServer().getPluginManager().getPlugin("MobArena");
        loadSettings();

        // Adds "/ma tienda" to MobArena's own command handler. MobArena always allows
        // /ma inside the arena, while other commands are blocked by allowed-commands.
        PluginCommand maCommand = mobArena.getCommand("ma");
        if (maCommand != null && maCommand.getExecutor() instanceof CommandHandler handler) {
            handler.register(new TiendaCommand(this));
        } else {
            getLogger().warning("Could not register /ma shop; players can still open the shop by right clicking the coin.");
        }

        resurrecciones = new Resurrecciones(this);
        powerups = new Powerups(this);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        getServer().getPluginManager().registerEvents(resurrecciones, this);
        getServer().getPluginManager().registerEvents(powerups, this);
        getServer().getPluginManager().registerEvents(powerups.lanzanieves(), this);
        getServer().getPluginManager().registerEvents(powerups.booom(), this);
        getServer().getPluginManager().registerEvents(powerups.velocidad(), this);
        getServer().getPluginManager().registerEvents(caja, this);
        contador.start();
        zombiesRapidos.start();
    }

    @Override
    public void onDisable() {
        if (powerups != null) {
            powerups.shutdown();
        }
        contador.stop();
        zombiesRapidos.stop();
    }

    /** The settings of config.yml, tienda.yml and powerups.yml merged together. */
    @Override
    public FileConfiguration getConfig() {
        return configFiles.merged();
    }

    @Override
    public void reloadConfig() {
        configFiles.load();
    }

    @Override
    public void saveDefaultConfig() {
        configFiles.load();
    }

    /** The files are only written by ConfigFiles (new options, migration). */
    @Override
    public void saveConfig() {
    }

    void loadSettings() {
        reloadConfig();
        loadMessages();
        Material configured = Material.matchMaterial(getConfig().getString("moneda.material", "GOLD_NUGGET"));
        if (configured == null || !configured.isItem()) {
            getLogger().warning("moneda.material is not a valid item; using GOLD_NUGGET.");
            configured = Material.GOLD_NUGGET;
        }
        coin = configured;
        tienda = Tienda.load(this);
        caja.load();
    }

    MobArena mobArena() {
        return mobArena;
    }

    Tienda tienda() {
        return tienda;
    }

    Material coin() {
        return coin;
    }

    Resurrecciones resurrecciones() {
        return resurrecciones;
    }

    CajaMisteriosa caja() {
        return caja;
    }

    /** True when the player is fighting in an arena (not in the lobby or spectating). */
    boolean isPlaying(Player player) {
        Arena arena = mobArena.getArenaMaster().getArenaWithPlayer(player);
        return arena != null && arena.inArena(player);
    }

    void openShop(Player player) {
        if (!player.hasPermission("mobarenatienda.use")) {
            player.sendMessage(text("mensajes.sin-permiso"));
            return;
        }
        if (!isPlaying(player)) {
            player.sendMessage(text("mensajes.solo-en-partida"));
            return;
        }
        TiendaMenu menu = new TiendaMenu(this, player);
        menu.render();
        player.openInventory(menu.getInventory());
    }

    void openReviveMenu(Player player) {
        if (!isPlaying(player)) {
            player.sendMessage(text("mensajes.solo-en-partida"));
            return;
        }
        RevivirMenu menu = new RevivirMenu(this, player);
        menu.render();
        player.openInventory(menu.getInventory());
    }

    Component text(String path) {
        return text(path, Map.of());
    }

    /**
     * messages_&lt;language&gt;.yml (English and Spanish bundled; any other language starts as a
     * copy of the English file). Missing keys fall back to the bundled file of that language.
     */
    private void loadMessages() {
        String language = getConfig().getString("language", DEFAULT_LANGUAGE).trim().toLowerCase(Locale.ROOT);
        if (!language.matches("[a-z0-9_-]{1,16}")) {
            getLogger().warning("Invalid language '" + language + "'; using " + DEFAULT_LANGUAGE + ".");
            language = DEFAULT_LANGUAGE;
        }
        String bundled = "messages_" + language + ".yml";
        if (getResource(bundled) == null) {
            bundled = "messages_" + DEFAULT_LANGUAGE + ".yml";
        }
        File file = new File(getDataFolder(), "messages_" + language + ".yml");
        if (!file.exists()) {
            try (InputStream in = getResource(bundled)) {
                Files.createDirectories(file.toPath().getParent());
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not create " + file.getName(), e);
            }
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = getResource(bundled)) {
            messageDefaults = in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            messageDefaults = new YamlConfiguration();
        }
    }

    /** Messages file first, then its bundled defaults, then the config files (shop titles, item names...). */
    private String rawText(String path) {
        if (messages.isString(path)) {
            return messages.getString(path);
        }
        if (messageDefaults.isString(path)) {
            return messageDefaults.getString(path);
        }
        return getConfig().getString(path, path);
    }

    private List<String> rawList(String path) {
        if (messages.isList(path)) {
            return messages.getStringList(path);
        }
        if (messageDefaults.isList(path)) {
            return messageDefaults.getStringList(path);
        }
        return getConfig().getStringList(path);
    }

    Component text(String path, Map<String, String> placeholders) {
        return colorize(applyPlaceholders(rawText(path), placeholders));
    }

    /** A list of lines, e.g. an item's lore. */
    List<Component> textList(String path, Map<String, String> placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(path)) {
            lines.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return lines;
    }

    private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    /** Translates '&' color codes; item texts must not be italic by default. */
    static Component colorize(String input) {
        return LEGACY.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
