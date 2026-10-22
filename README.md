# DetailStick

A **server-side** [NeoForge](https://neoforged.net/) mod for **Minecraft 1.21.1** that turns the
vanilla debug stick into a survival-friendly block state editor — one that **cannot be used to
duplicate items or fluids**.

The debug stick is a powerful building tool, but in vanilla it only works in creative mode and lets
players inflate "count" block states (turtle eggs, sea pickles, candles, snow layers) or toggle
waterlogging to create resources from nothing. DetailStick fixes both problems: it makes the stick
usable in survival (for operators by default) while refusing to touch the properties that enable
duplication.

## Features

- **Fully server-side.** No custom items are registered. The mod operates on the vanilla
  `minecraft:debug_stick`, so players can connect with an unmodified vanilla client and need nothing
  installed. Only the server requires NeoForge + this mod.
- **Works in survival.** The vanilla debug stick is inert outside creative mode; DetailStick drives
  the block-state edits server-side so it works in survival too.
- **Duplication protection.** Properties that can be abused to spawn items/fluids are filtered out
  entirely — they can neither be selected nor changed.
- **Configurable.** The blocked property list and the permission requirement are both adjustable via
  a server config file.
- **Craftable "Detail Stick".** A simple recipe yields a debug stick renamed to *Detail Stick*.

## Controls

Hold the Detail Stick (a debug stick) and interact with a block:

| Action | Effect |
| --- | --- |
| **Left-click** a block | Select the next editable block state property |
| **Right-click** a block | Cycle the selected property's value |
| **Sneak** + click | Cycle in reverse |

The currently selected property and its new value are shown on your action bar. Blocked properties
are skipped automatically and never appear as selectable.

## Crafting

```
[ ][E]
[S][ ]
```

- `E` = Emerald (top-right)
- `S` = Stick (bottom-left)

Crafting is **not** permission-gated — any player can craft and hold the Detail Stick. Whether they
can *use* it to edit blocks is controlled by the `requirePermission` config option (see below).

## Duplication protection

By default, the Detail Stick refuses to edit these block state properties:

| Property | Blocks affected | Why it's blocked |
| --- | --- | --- |
| `eggs` | Turtle eggs (1–4) | Incrementing the count duplicates eggs on break |
| `pickles` | Sea pickles (1–4) | Incrementing the count duplicates sea pickles |
| `candles` | Candles / all dyed candles (1–4) | Incrementing the count duplicates candles |
| `layers` | Snow layers (1–8) | Incrementing the count duplicates snowballs |
| `waterlogged` | Any waterloggable block | Toggling it creates a free, harvestable water source |

The list is matched by property **name**, case-insensitively, so blocking `candles` automatically
covers every candle colour.

## Configuration

Server config lives at `config/detailstick-server.toml` and is generated on first launch.

```toml
# Block state property names the Detail Stick is NOT allowed to edit.
blockedProperties = ["eggs", "pickles", "candles", "layers", "waterlogged"]

# If true, only operators (permission level 2+) can edit block states with the stick.
# If false, any player holding it can edit block states.
# Either way, anyone can craft and hold the stick - this only gates its use.
requirePermission = true
```

- **`blockedProperties`** — add or remove property names to adjust what the stick may edit. Add a
  property name here to block it; remove one to allow editing it again.
- **`requirePermission`** — set to `false` to let non-operator players use the stick to edit blocks.

> **Note:** config changes require a server restart. Because this is a `SERVER`-type config, editing
> the file only affects that server.

## Installation

1. Install [NeoForge](https://neoforged.net/) **21.1.x** for Minecraft **1.21.1** on your server.
2. Drop `detailstick-<version>.jar` into the server's `mods/` folder.
3. Start the server once to generate `config/detailstick-server.toml`, adjust it if desired, then
   restart.

Clients do **not** need the mod — they can join with a vanilla (or any NeoForge) client.

## Building from source

Requires JDK 21.

```bash
./gradlew build      # Linux/macOS
gradlew.bat build    # Windows
```

The built jar is written to `build/libs/detailstick-<version>.jar`.

## Compatibility

- Minecraft **1.21.1**
- NeoForge **21.1.x** (`[21,)`)
- Tested alongside a Create-based modpack. Because the recipe uses the explicit ingredient object
  form (`{"item": "..."}`), it loads correctly even when stricter recipe codecs (e.g. Forgified
  Fabric API) are present.

## License

All Rights Reserved. See `gradle.properties` (`mod_license`) and update as desired.

## Credits

Authors: TheAndromedaCat, A Little Bit of Everything Team, Not a Glitch Studios
