package dev.mobarenatienda;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The configuration is split into several files, each owning some top-level sections.
 * They are merged into one in-memory configuration, so the rest of the plugin keeps
 * reading paths like "tienda.articulos" or "powerups.probabilidad" as before.
 */
final class ConfigFiles {

    private static final String MAIN = "config.yml";

    /** Top-level sections that live outside config.yml, by file. */
    private static final Map<String, List<String>> SPLIT = new LinkedHashMap<>();

    static {
        SPLIT.put("tienda.yml", List.of("tienda", "caja-misteriosa", "revivir"));
        SPLIT.put("powerups.yml", List.of("powerups"));
    }

    /**
     * Lists the user fully controls: defaults are never merged into them, so removed
     * articles or prizes don't come back.
     */
    private static final List<String> USER_LISTS = List.of("tienda.articulos", "caja-misteriosa.premios");

    private final File folder;
    private final Function<String, InputStream> resources;
    private final Logger logger;
    private YamlConfiguration merged = new YamlConfiguration();

    /** {@code resources} opens a default file bundled in the jar (null if missing). */
    ConfigFiles(File folder, Function<String, InputStream> resources, Logger logger) {
        this.folder = folder;
        this.resources = resources;
        this.logger = logger;
    }

    YamlConfiguration merged() {
        return merged;
    }

    void load() {
        folder.mkdirs();
        migrateSingleFile();

        YamlConfiguration result = new YamlConfiguration();
        for (String name : fileNames()) {
            YamlConfiguration yaml = loadFile(name);
            for (String key : yaml.getKeys(true)) {
                if (!yaml.isConfigurationSection(key)) {
                    result.set(key, yaml.get(key));
                }
            }
        }
        merged = result;
    }

    private static List<String> fileNames() {
        return List.of(MAIN, "tienda.yml", "powerups.yml");
    }

    /** Loads one file, creating it from the jar if missing and adding options new versions introduced. */
    private YamlConfiguration loadFile(String name) {
        File file = new File(folder, name);
        if (!file.exists()) {
            copyResource(name, file);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = resource(name);
        if (defaults == null) {
            return yaml;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || isInUserList(key) || yaml.contains(key)) {
                continue;
            }
            yaml.set(key, defaults.get(key));
            yaml.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            save(yaml, file);
        }
        return yaml;
    }

    /**
     * Older versions kept everything in config.yml. Moves those sections (with the
     * server's own values) into their new files, after backing up config.yml.
     */
    private void migrateSingleFile() {
        File mainFile = new File(folder, MAIN);
        if (!mainFile.exists()) {
            return;
        }
        YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        boolean needed = SPLIT.values().stream().flatMap(List::stream).anyMatch(main::isConfigurationSection);
        if (!needed) {
            return;
        }

        File backup = new File(folder, "config.yml.backup-antes-de-dividir");
        try {
            if (!backup.exists()) {
                Files.copy(mainFile.toPath(), backup.toPath());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not back up config.yml; it was not split.", e);
            return;
        }

        for (Map.Entry<String, List<String>> entry : SPLIT.entrySet()) {
            File targetFile = new File(folder, entry.getKey());
            if (!targetFile.exists()) {
                copyResource(entry.getKey(), targetFile);
            }
            YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
            boolean moved = false;
            for (String section : entry.getValue()) {
                if (!main.isConfigurationSection(section)) {
                    continue;
                }
                // The server's own articles/prizes replace the default ones entirely,
                // keeping the explanation comment written above the list.
                Map<String, List<String>> listComments = new LinkedHashMap<>();
                for (String list : USER_LISTS) {
                    if (list.startsWith(section + ".") && main.isConfigurationSection(list)) {
                        listComments.put(list, target.getComments(list));
                        target.set(list, null);
                    }
                }
                for (String key : main.getConfigurationSection(section).getKeys(true)) {
                    String path = section + "." + key;
                    if (!main.isConfigurationSection(path)) {
                        target.set(path, main.get(path));
                    }
                }
                listComments.forEach(target::setComments);
                main.set(section, null);
                moved = true;
            }
            if (moved) {
                save(target, targetFile);
            }
        }
        // The old header described a single file.
        YamlConfiguration defaults = resource(MAIN);
        if (defaults != null) {
            main.options().setHeader(defaults.options().getHeader());
        }
        save(main, mainFile);
        logger.info("config.yml split into config.yml, tienda.yml and powerups.yml "
                + "(the original is kept as config.yml.backup-antes-de-dividir).");
    }

    private static boolean isInUserList(String key) {
        for (String list : USER_LISTS) {
            if (key.startsWith(list + ".")) {
                return true;
            }
        }
        return false;
    }

    private YamlConfiguration resource(String name) {
        InputStream in = resources.apply(name);
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private void copyResource(String name, File file) {
        try (InputStream in = resources.apply(name)) {
            if (in != null) {
                Files.copy(in, file.toPath());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not create " + name, e);
        }
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }
}
