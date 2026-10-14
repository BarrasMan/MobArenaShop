# MobArenaShop – Configuration guide

All files are in `plugins/MobArenaShop/`. Texts accept `&` color codes. After changing anything, run `/ma shop reload`.

The setting names are in Spanish. This guide (and the comments inside each file) explains every one.

## config.yml

| Setting | Default | Description |
|---|---|---|
| `language` | `en` | Messages file to use: `en`, `es` or any code you translate yourself |
| `moneda.material` | `GOLD_NUGGET` | Item used as the coin |
| `moneda.por-mob` | `1` | Coins for each mob the player kills |
| `moneda.por-jefe` | `10` | Coins for killing a MobArena boss |
| `abrir-con-moneda` | `true` | Right click with the coin in hand opens the shop |
| `contador-mobs.activado` | `true` | Boss bar with the mobs left in the wave |
| `zombies-rapidos.activado` | `true` | Zombies get faster as the game goes on |
| `zombies-rapidos.cada-oleadas` | `5` | Every how many waves they get faster |
| `zombies-rapidos.porcentaje` | `7` | How much faster each time (%): +7% at wave 5, +14% at wave 10... |
| `zombies-rapidos.tipos` | zombie types | Mob types it applies to |

If you change the coin item, also change the texts that say "gold nugget(s)" in the messages file.

## tienda.yml

### The shop (`tienda`)

| Setting | Description |
|---|---|
| `titulo` | Menu title |
| `filas` | Rows of the menu (1-6) |
| `slot-info` | Slot that shows the player's coins (`-1` to hide it) |
| `relleno` | Item that fills the empty slots (`AIR` for none) |
| `articulos` | The articles for sale (see below) |

Slots go from `0` (top left) to `filas × 9 − 1`, with 9 slots per row.

Each article (`tienda.articulos.<any-id>`):

| Key | Required | Description |
|---|---|---|
| `slot` | yes | Position in the menu |
| `material` | yes | Minecraft material, e.g. `DIAMOND_SWORD` |
| `precio` | yes | Price in coins |
| `cantidad` | no | Amount (1 by default) |
| `encantamientos` | no | `enchantment: level` pairs; levels above vanilla are allowed |
| `pocion` | no | Potion type for `POTION`, `SPLASH_POTION`, `LINGERING_POTION` and `TIPPED_ARROW` |
| `nombre` | no | Custom item name |

Example: a stack of 32 arrows for 5 coins in slot 8:

```yaml
    more_arrows:
      slot: 8
      material: ARROW
      cantidad: 32
      precio: 5
```

Example: a splash potion of night vision:

```yaml
    night_vision_potion:
      slot: 42
      material: SPLASH_POTION
      pocion: NIGHT_VISION
      precio: 4
```

Articles with a wrong material, enchantment, potion or slot are skipped, and the console says why.

### Mystery box (`caja-misteriosa`)

| Setting | Description |
|---|---|
| `activado` | Show it in the shop |
| `precio` | Price in coins |
| `slot` | Slot in the shop (must be free) |
| `material`, `nombre`, `lore` | How the button looks |
| `titulo` | Title of the roulette menu |
| `animacion` | `false` gives the prize instantly, without the roulette |
| `premios` | The prizes |

Each prize uses the same keys as the shop articles (`material`, `encantamientos`, `nombre`...), plus:

| Key | Description |
|---|---|
| `peso` | Weight: how likely it is compared to the others |
| `rareza` | Rarity text shown under the name |
| `anunciar` | `true` to tell the whole team when someone gets it |

The default weights add up to 100, so each weight is its % chance: Rare 55%, Epic 35%, Legendary 10%.

### Reviving teammates (`revivir`)

| Setting | Description |
|---|---|
| `activado` | Show the revive button in the shop |
| `precio` | Price in coins |
| `slot` | Slot of the button |

How it works: a living player pays for a fallen teammate (who is spectating). When the next wave starts, the teammate comes back next to the payer, with full health and their class kit. If the payer dies before that, the revive is lost. Only one player can pay for each fallen teammate.

## powerups.yml

| Setting | Description |
|---|---|
| `probabilidad` | Chance (%) that a mob drops a power-up |
| `segundos-en-suelo` | Seconds the power-up stays on the ground |

Every power-up has `activado` (enabled), `peso` (weight, how often it is picked compared to the others) and `nombre` (the name floating over it). Extra settings:

| Power-up | Settings |
|---|---|
| `lanzanieves` (snowball launcher) | `duracion` seconds, `danio` damage per snowball (2 = 1 heart), `cadencia-ticks` ticks between shots, `velocidad` snowball speed, `material-arma` the weapon item |
| `vida` (health) | none |
| `booom` | `mecha` seconds until the TNT explodes, `matar-jefes` whether it also kills bosses |
| `velocidad` (speed) | `duracion` seconds |

Kills made by BOOOM count as the player's (they give coins) but don't drop more power-ups.

## messages_<language>.yml

Every message (`mensajes`) and menu text (`gui`), including the mob counter (`gui.contador-mobs`). Words between `{braces}` are replaced automatically. Keep them as they are.

English (`messages_en.yml`) and Spanish (`messages_es.yml`) are included. To translate into another language, set `language: <code>` in `config.yml` and run `/ma shop reload`. A copy of the English file is created as `messages_<code>.yml`, ready to translate.
