# MobArenaTienda

**An unofficial addon for [MobArena](https://github.com/garbagemule/MobArena)** that adds an in-arena economy. Players earn gold nuggets for every mob they kill and spend them in a shop during the game. On top of that come power-ups, a mystery box and the option to revive fallen teammates.

> This is a separate plugin. It does **not** modify MobArena, and it is not made or endorsed by the MobArena authors. MobArena must be installed on the server.

## Features

- **Coins for every kill**: 1 gold nugget per mob and 10 per boss (configurable). The nuggets go straight into the player's inventory.
- **In-arena shop** (`/ma shop`, or right click with a gold nugget): weapons, armor, food and splash potions, all configurable with enchantments and potion types. Armor pieces are equipped automatically if that slot is empty.
- **Revive teammates**: pay in the shop for a fallen teammate. They come back, with their class kit and next to you, when the next wave starts, as long as you are still alive.
- **Mystery box**: a roulette with Rare, Epic and Legendary prizes with "impossible" enchantments. Legendary prizes are announced to the team.
- **Power-ups** that mobs can drop, glowing on the ground for a few seconds:
  - **Snowball launcher**: a hoe that shoots unlimited snowballs
  - **Health**: full heal
  - **BOOOM**: throw a TNT that wipes out the whole wave (no block or player damage)
  - **Speed x2**: with a boss bar timer
- **Mob counter**: a boss bar with the mobs left in the current wave, for players and spectators.
- **Faster zombies**: zombies run a bit faster every few waves (configurable).
- **English and Spanish** messages included. Any other language can be added by translating one file.

## Requirements

| | |
|---|---|
| Server | **Paper 26.1.x** |
| Java | **25** |
| Required | **[MobArena](https://github.com/garbagemule/MobArena) 0.109** |

> **Reviving teammates** needs access to MobArena internals, because MobArena has no API to bring a spectator back into the fight. It is written and tested against **MobArena 0.109**. With another MobArena version, everything else keeps working, but reviving may fail. In that case it logs an error, and the player simply stays as a spectator.

## Installation

1. Install MobArena and set up your arenas as usual.
2. Download `MobArenaTienda-1.0.0.jar` and put it in your `plugins` folder.
3. Restart the server.
4. Tweak the files in `plugins/MobArenaTienda/` and run `/ma shop reload`.

Players open the shop with `/ma shop` (MobArena always allows `/ma` commands inside the arena) or by right clicking with a gold nugget in hand.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ma shop` | Open the arena shop (only while playing) | `mobarenatienda.use` |
| `/ma shop reload` | Reload every configuration file | `mobarenatienda.admin` |

`/ma tienda` and `/ma tienda recargar` also work (Spanish aliases).

## Permissions

| Permission | Default | Description |
|---|---|---|
| `mobarenatienda.use` | everyone | Open the arena shop |
| `mobarenatienda.admin` | op | Reload the configuration |

## Configuration

| File | Contents |
|---|---|
| `config.yml` | Language, coin item and amounts, mob counter, faster zombies |
| `tienda.yml` | Shop layout and articles, mystery box and its prizes, reviving teammates |
| `powerups.yml` | Drop chance, time on the ground and each power-up |
| `messages_<language>.yml` | Every message and menu text |

The setting names are in Spanish (for example `precio` = price). The comments in every file explain each one in English. The full reference, with examples of how to add your own articles and prizes, is in **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**.

Language: `language: en` or `language: es` in `config.yml`. For another language (for example `fr`), set `language: fr` and run `/ma shop reload`. A `messages_fr.yml` copy of the English file is created, ready to translate.

## Building from source

Requires **JDK 25** and **Maven**:

```
mvn package
```

The plugin jar is created at `target/MobArenaTienda-1.0.0.jar`.

## License

MobArenaTienda is free software, released under the **[GNU General Public License v3.0](LICENSE)**, the same license as MobArena.
